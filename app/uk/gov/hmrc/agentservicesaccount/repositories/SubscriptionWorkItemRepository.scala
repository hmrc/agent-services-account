/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentservicesaccount.repositories

import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.typesafe.config.Config
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.FindOneAndUpdateOptions
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.ReturnDocument
import org.mongodb.scala.model.Updates
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.Logging
import play.api.Logger
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mongo.workitem.WorkItemFields
import uk.gov.hmrc.mongo.workitem.WorkItemRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.logging.ObservableFutureImplicits.*
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*
import scala.util.control.NonFatal

@Singleton
class SubscriptionWorkItemRepository @Inject() (
  config: Config,
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter & Decrypter
)
extends WorkItemRepository[SubscriptionWorkItem](
  collectionName = "subscription-work-items",
  mongoComponent = mongoComponent,
  itemFormat = SubscriptionWorkItem.mongoFormat,
  workItemFields = WorkItemFields.default,
  extraIndexes = Seq(
    IndexModel(
      Indexes.ascending("item.arn", "item.regime"),
      IndexOptions()
        .name("uniqueArnRegime")
        .unique(true)
    )
  )
)
with Logging:

  // Unique per (arn, regime) to prevent multiple concurrent subscription attempts for the same regime.
  // Retries after PermanentlyFailed are supported by deleting the old work item and inserting a fresh attempt.
  lazy val coll: MongoCollection[WorkItem[SubscriptionWorkItem]] = collection // necessary to avoid IntelliJ "Cannot resolve symbol 'collection'" error

  override lazy val requiresTtlIndex = false // TODO do we need a TTL to clean up permanently failed items?

  override def now(): Instant = Instant.now()

  override def inProgressRetryAfter: Duration = config.getDuration("work-item-repository.subscriptions.retry-in-progress-after")

  override def ensureIndexes(): Future[Seq[String]] =
    // Migration: older environments may already have the `uniqueArn` index (unique on item.arn) from main.
    // That index blocks concurrent PAYE + SA work items for the same ARN, so we drop it and replace with
    // the new unique (arn, regime) index.
    dropLegacyUniqueArnIndexIfPresent().flatMap(_ => super.ensureIndexes())

  // Protected for unit testing of the "fail fast" migration behaviour.
  protected def dropLegacyUniqueArnIndexIfPresent(): Future[Unit] = SubscriptionWorkItemRepository.dropLegacyUniqueArnIndexIfPresent(
    listIndexNames = coll.listIndexes().toFuture().map { indexes =>
      indexes.flatMap(_.get("name").map(_.asString().getValue))
    },
    dropIndex = name => coll.dropIndex(name).toFuture().map(_ => ()),
    logger = SubscriptionWorkItemRepository.indexMigrationLogger
  )

  def findByArnAndRegime(
    arn: Arn,
    regime: LegacyRegime
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = {
    coll.find(
      Filters.and(
        Filters.equal("item.arn", arn.value),
        Filters.equal("item.regime", regime.toString)
      )
    ).toFuture()
      .map(_.headOption)
  }

  def pullOutstandingForRegime(
    regime: LegacyRegime,
    failedBefore: Instant,
    availableBefore: Instant
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = {
    def findNextItemByQuery(query: org.bson.conversions.Bson): Future[Option[WorkItem[SubscriptionWorkItem]]] = coll
      .findOneAndUpdate(
        filter = query,
        update = Updates.combine(
          Updates.set(workItemFields.status, ProcessingStatus.InProgress),
          Updates.set(workItemFields.updatedAt, now())
        ),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()

    def baseFilter(status: ProcessingStatus): org.bson.conversions.Bson = Filters.and(
      Filters.equal(workItemFields.status, status),
      Filters.equal(s"${workItemFields.item}.regime", regime.toString),
      Filters.exists(s"${workItemFields.item}.agentReference", true)
    )

    def todoQuery: org.bson.conversions.Bson = Filters.and(
      baseFilter(ProcessingStatus.ToDo),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    def failedQuery: org.bson.conversions.Bson = Filters.and(
      baseFilter(ProcessingStatus.Failed),
      Filters.lt(workItemFields.updatedAt, failedBefore),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    def inProgressQuery: org.bson.conversions.Bson = Filters.and(
      baseFilter(ProcessingStatus.InProgress),
      Filters.lt(workItemFields.updatedAt, now().minus(inProgressRetryAfter))
    )

    findNextItemByQuery(todoQuery).flatMap {
      case None =>
        findNextItemByQuery(failedQuery)
          .flatMap {
            case None => findNextItemByQuery(inProgressQuery)
            case item => Future.successful(item)
          }
      case item => Future.successful(item)
    }
  }

  def pullOutstandingRobotics(
    regime: LegacyRegime,
    availableBefore: Instant
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = {
    def findNextItemByQuery(query: org.bson.conversions.Bson): Future[Option[WorkItem[SubscriptionWorkItem]]] = coll
      .findOneAndUpdate(
        filter = query,
        update = Updates.combine(
          Updates.set(workItemFields.status, ProcessingStatus.InProgress),
          Updates.set(workItemFields.updatedAt, now())
        ),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()

    def baseFilter(status: ProcessingStatus): org.bson.conversions.Bson = Filters.and(
      Filters.equal(workItemFields.status, status),
      Filters.equal(s"${workItemFields.item}.regime", regime.toString),
      Filters.exists(s"${workItemFields.item}.agentReference", false)
    )

    def todoQuery: org.bson.conversions.Bson = Filters.and(
      baseFilter(ProcessingStatus.ToDo),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    // Deferred items are intentionally excluded here. They are left for dedicated retry policy work (APB-10572).
    //
    // NOTE: for robotics-invocation flows, InProgress means "invocation already sent and awaiting callback".
    // Re-pulling all InProgress items risks duplicate outbound submissions if the callback arrives after
    // `retry-in-progress-after`. However, we must still recover work items that are stuck InProgress because the
    // service crashed after pulling them and before sending the outbound call. We use `item.roboticsInvokedAt` as a
    // marker to distinguish "picked but not invoked yet" vs "invoked and awaiting callback".
    def inProgressNotInvokedQuery: org.bson.conversions.Bson = Filters.and(
      baseFilter(ProcessingStatus.InProgress),
      Filters.exists(s"${workItemFields.item}.roboticsInvokedAt", false),
      Filters.lt(workItemFields.updatedAt, now().minus(inProgressRetryAfter))
    )

    findNextItemByQuery(todoQuery).flatMap {
      case None => findNextItemByQuery(inProgressNotInvokedQuery)
      case item => Future.successful(item)
    }
  }
  def findByRequestId(
    requestId: String
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = coll.find(Filters.equal("item.requestId", requestId)).toFuture().map(_.headOption)

  def addAgentReference(
    agentReference: AgentReference,
    requestId: String
  ): Future[Boolean] =
    // Callback success does not mean the subscription is complete; it unblocks the next workflow stage.
    // We update the agentReference and return the work item to ToDo so the post-callback worker can pick it up.
    // We also guard against overwriting terminal states.
    coll.updateOne(
      // Filter by status as well as requestId so a success callback cannot revive a terminal work item if the status
      // changes between read and write.
      Filters.and(
        Filters.equal("item.requestId", requestId),
        // Avoid overwriting already-advanced work items (e.g. post-callback worker has claimed it InProgress) back to
        // ToDo on duplicate success callbacks.
        Filters.exists(s"${workItemFields.item}.agentReference", false),
        Filters.or(
          Filters.equal(workItemFields.status, ProcessingStatus.ToDo),
          Filters.equal(workItemFields.status, ProcessingStatus.InProgress),
          Filters.equal(workItemFields.status, ProcessingStatus.Failed),
          Filters.equal(workItemFields.status, ProcessingStatus.Deferred)
        )
      ),
      Updates.combine(
        Updates.set("item.agentReference", agentReference.value),
        Updates.set(workItemFields.status, ProcessingStatus.ToDo),
        Updates.set(workItemFields.updatedAt, now()),
        Updates.set(workItemFields.availableAt, now())
      )
    ).toFuture()
      .flatMap { update =>
        if update.getModifiedCount > 0 then Future.successful(true)
        else
          findByRequestId(requestId).map {
            case Some(workItem) if workItem.item.agentReference.isDefined =>
              logger.warn(s"[SubscriptionWorkItemRepository][addAgentReference] Agent reference for $requestId was already set previously, duplicate callback received")
              true
            case Some(workItem) if workItem.status == PermanentlyFailed =>
              logger.warn(s"[SubscriptionWorkItemRepository][addAgentReference] Ignoring success callback for $requestId because work item is PermanentlyFailed")
              true
            case Some(workItem) if workItem.status == ProcessingStatus.Succeeded =>
              logger.warn(s"[SubscriptionWorkItemRepository][addAgentReference] Ignoring success callback for $requestId because work item is Succeeded")
              true
            case _ => false
          }
      }

  def deletePermanentlyFailedById(workItemId: ObjectId): Future[Boolean] = coll.deleteOne(
    Filters.and(
      Filters.equal("_id", workItemId),
      Filters.equal(workItemFields.status, PermanentlyFailed)
    )
  ).toFuture()
    .map(_.getDeletedCount > 0)

  def markAsDeferredIfStillAwaitingInvocation(workItemId: ObjectId): Future[Boolean] =
    // Guard against a race where the outbound invoke fails/times out locally but the upstream has accepted it and a
    // success callback has already moved the work item to ToDo with an agentReference. In that case we must not
    // overwrite the callback transition by marking it Deferred.
    coll
      .updateOne(
        Filters.and(
          Filters.equal("_id", workItemId),
          Filters.equal(workItemFields.status, ProcessingStatus.InProgress),
          Filters.exists(s"${workItemFields.item}.agentReference", false)
        ),
        Updates.combine(
          Updates.set(workItemFields.status, ProcessingStatus.Deferred),
          Updates.set(workItemFields.updatedAt, now())
        )
      )
      .toFuture()
      .map(_.getModifiedCount > 0)

  def handleFailureCallback(requestId: String): Future[SubscriptionWorkItemRepository.FailureCallbackHandling] = coll
    .updateOne(
      Filters.and(
        Filters.equal("item.requestId", requestId),
        // Guard against overwriting a successful callback transition.
        Filters.exists(s"${workItemFields.item}.agentReference", false),
        Filters.or(
          Filters.equal(workItemFields.status, ProcessingStatus.ToDo),
          Filters.equal(workItemFields.status, ProcessingStatus.InProgress),
          Filters.equal(workItemFields.status, ProcessingStatus.Failed),
          Filters.equal(workItemFields.status, ProcessingStatus.Deferred)
        )
      ),
      Updates.combine(
        Updates.set(workItemFields.status, PermanentlyFailed),
        Updates.set(workItemFields.updatedAt, now())
      )
    )
    .toFuture()
    .flatMap { update =>
      if update.getModifiedCount > 0 then Future.successful(SubscriptionWorkItemRepository.FailureCallbackHandling.MarkedPermanentlyFailed)
      else
        findByRequestId(requestId).map {
          case Some(workItem) if workItem.status == PermanentlyFailed => SubscriptionWorkItemRepository.FailureCallbackHandling.AlreadyPermanentlyFailed
          case Some(workItem) if workItem.item.agentReference.isDefined || workItem.status == ProcessingStatus.Succeeded =>
            SubscriptionWorkItemRepository.FailureCallbackHandling.IgnoredAlreadySucceeded
          case _ => SubscriptionWorkItemRepository.FailureCallbackHandling.NotFound
        }
    }

  def saveStatusToDatabase(
    workItemId: ObjectId,
    status: ProcessingStatus,
    failedCounter: Int,
    invokedAt: Instant = now()
  ): Future[Boolean] = coll
    .updateOne(
      Filters.equal("_id", workItemId),
      Updates.combine(
        Updates.set(workItemFields.status, status),
        Updates.set(workItemFields.failureCount, failedCounter),
        Updates.set(s"${workItemFields.item}.roboticsInvokedAt", invokedAt),
        Updates.set(workItemFields.updatedAt, now())
      )
    )
    .toFuture()
    .map(_.getModifiedCount > 0)

object SubscriptionWorkItemRepository:

  private[repositories] val indexMigrationLogger: Logger = Logger("uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository")

  enum FailureCallbackHandling:

    case MarkedPermanentlyFailed
    case AlreadyPermanentlyFailed
    case IgnoredAlreadySucceeded
    case NotFound

  private[repositories] def dropLegacyUniqueArnIndexIfPresent(
    listIndexNames: => Future[Seq[String]],
    dropIndex: String => Future[Unit],
    logger: Logger
  )(using ExecutionContext): Future[Unit] = listIndexNames
    .flatMap { names =>
      val hasLegacyUniqueArn = names.contains("uniqueArn")
      if hasLegacyUniqueArn then
        logger.warn("[SubscriptionWorkItemRepository][ensureIndexes] Dropping legacy uniqueArn index in favour of uniqueArnRegime")
        dropIndex("uniqueArn")
      else
        Future.unit
    }
    .recoverWith { case NonFatal(error) =>
      // This migration is required for correctness: leaving the legacy uniqueArn index in place blocks cross-regime
      // subscriptions (e.g. PAYE + SA) by incorrectly enforcing uniqueness on arn alone.
      logger.error("[SubscriptionWorkItemRepository][ensureIndexes] Failed to check/drop legacy uniqueArn index", error)
      Future.failed(error)
    }
