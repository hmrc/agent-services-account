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

import com.typesafe.config.Config
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.{AgentReference, LegacyRegime, SubscriptionWorkItem}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.logging.ObservableFutureImplicits.*
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemFields, WorkItemRepository}

import java.time.{Duration, Instant}
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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

  // Retry work items stuck in progress
  override def inProgressRetryAfter: Duration = config.getDuration("work-item-repository.subscriptions.retry-in-progress-after")

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

  def pullAwaitingKnownFacts(
    regime: LegacyRegime,
    failedBefore: Instant,
    availableBefore: Instant
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = {
    def findNextItemByQuery(query: Bson): Future[Option[WorkItem[SubscriptionWorkItem]]] = coll
      .findOneAndUpdate(
        filter = query,
        update = Updates.combine(
          Updates.set(workItemFields.status, ProcessingStatus.InProgress),
          Updates.set(workItemFields.updatedAt, now())
        ),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()

    def baseFilter(status: ProcessingStatus): Bson = Filters.and(
      Filters.equal(workItemFields.status, status),
      Filters.equal(s"${workItemFields.item}.regime", regime.toString),
      Filters.exists(s"${workItemFields.item}.agentReference", true)
    )

    def todoQuery: Bson = Filters.and(
      baseFilter(ProcessingStatus.ToDo),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    def failedQuery: Bson = Filters.and(
      baseFilter(ProcessingStatus.Failed),
      Filters.lt(workItemFields.updatedAt, failedBefore),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    def inProgressQuery: Bson = Filters.and(
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

  def pullAwaitingRobotics(
    regime: LegacyRegime,
    failedBefore: Instant,
    availableBefore: Instant
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = {
    def findNextItemByQuery(query: Bson): Future[Option[WorkItem[SubscriptionWorkItem]]] = coll
      .findOneAndUpdate(
        filter = query,
        update = Updates.combine(
          Updates.set(workItemFields.status, ProcessingStatus.InProgress),
          Updates.set(workItemFields.updatedAt, now())
        ),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      ).toFutureOption()

    def baseFilter(status: ProcessingStatus): Bson = Filters.and(
      Filters.equal(workItemFields.status, status),
      Filters.equal(s"${workItemFields.item}.regime", regime.toString),
      Filters.exists(s"${workItemFields.item}.agentReference", false),
      Filters.exists(s"${workItemFields.item}.roboticsInvokedAt", false)
    )

    def todoQuery: Bson = Filters.and(
      baseFilter(ProcessingStatus.ToDo),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    def failedQuery: Bson = Filters.and(
      baseFilter(ProcessingStatus.Failed),
      Filters.lt(workItemFields.updatedAt, failedBefore),
      Filters.lt(workItemFields.availableAt, availableBefore)
    )

    def inProgressQuery: Bson = Filters.and(
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

  def findByRequestId(
    requestId: String
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] = coll.find(Filters.equal("item.requestId", requestId)).toFuture().map(_.headOption)

  def addAgentReference(
    agentReference: AgentReference,
    requestId: String
  ): Future[Boolean] =
    // Callback success puts the work item into a state that can be picked up by the KnownFacts worker
    // Reset failure count so the robotics and known facts workers have separate retry limits
    coll.updateOne(
      Filters.and(
        Filters.equal("item.requestId", requestId),
        Filters.exists(s"${workItemFields.item}.agentReference", false),
        Filters.notEqual(workItemFields.status, ProcessingStatus.PermanentlyFailed)
      ),
      Updates.combine(
        Updates.set("item.agentReference", agentReference.value),
        Updates.set(workItemFields.status, ProcessingStatus.ToDo),
        Updates.set(workItemFields.updatedAt, now()),
        Updates.set(workItemFields.availableAt, now()),
        Updates.set(workItemFields.failureCount, 0)
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

  def markAsInvoked(workItemId: ObjectId): Future[Boolean] = {
    // Set roboticsInvokedAt so the robotics worker can ignore this item
    coll
      .updateOne(
        Filters.equal("_id", workItemId),
        Updates.combine(
          Updates.set(workItemFields.status, ProcessingStatus.ToDo),
          Updates.set(s"${workItemFields.item}.roboticsInvokedAt", now()),
          Updates.set(workItemFields.updatedAt, now())
        )
      )
      .toFuture()
      .map(_.getModifiedCount > 0)
  }

object SubscriptionWorkItemRepository:
  enum FailureCallbackHandling:

    case MarkedPermanentlyFailed
    case AlreadyPermanentlyFailed
    case IgnoredAlreadySucceeded
    case NotFound
