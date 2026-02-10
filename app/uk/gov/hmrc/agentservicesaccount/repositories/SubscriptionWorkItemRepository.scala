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
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, Indexes, ReturnDocument, Updates}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemFields, WorkItemRepository}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.logging.ObservableFutureImplicits.*
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.PermanentlyFailed

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
      Indexes.ascending("item.arn"),
      IndexOptions().name("uniqueArn").unique(true)
    )
  )
):
  // TODO set up unique ARN index (potentially needs to be partial index to avoid indexing permanently failed items),
  //  will need a custom method to wrap pushNew and handle duplicate errors caused by the index
  lazy val coll: MongoCollection[WorkItem[SubscriptionWorkItem]] = collection // necessary to avoid IntelliJ "Cannot resolve symbol 'collection'" error

  override lazy val requiresTtlIndex = false // TODO do we need a TTL to clean up permanently failed items?

  override def now(): Instant = Instant.now()

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

  def pullOutstandingPaye(failedBefore: Instant, availableBefore: Instant): Future[Option[WorkItem[SubscriptionWorkItem]]] = {
    def findNextItemByQuery(query: org.bson.conversions.Bson): Future[Option[WorkItem[SubscriptionWorkItem]]] =
      coll
        .findOneAndUpdate(
          filter = query,
          update = Updates.combine(
            Updates.set(workItemFields.status, ProcessingStatus.InProgress),
            Updates.set(workItemFields.updatedAt, now())
          ),
          options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        ).toFutureOption()

    def baseFilter(status: ProcessingStatus): org.bson.conversions.Bson =
      Filters.and(
        Filters.equal(workItemFields.status, status),
        Filters.equal(s"${workItemFields.item}.regime", LegacyRegime.PAYE.toString),
        Filters.exists(s"${workItemFields.item}.agentReference", true)
      )

    def todoQuery: org.bson.conversions.Bson =
      Filters.and(
        baseFilter(ProcessingStatus.ToDo),
        Filters.lt(workItemFields.availableAt, availableBefore)
      )

    def failedQuery: org.bson.conversions.Bson =
      Filters.and(
        baseFilter(ProcessingStatus.Failed),
        Filters.lt(workItemFields.updatedAt, failedBefore),
        Filters.lt(workItemFields.availableAt, availableBefore)
      )

    def inProgressQuery: org.bson.conversions.Bson =
      Filters.and(
        baseFilter(ProcessingStatus.InProgress),
        Filters.lt(workItemFields.updatedAt, now().minus(inProgressRetryAfter))
      )

    findNextItemByQuery(todoQuery).flatMap {
      case None => findNextItemByQuery(failedQuery)
          .flatMap {
            case None => findNextItemByQuery(inProgressQuery)
            case item => Future.successful(item)
          }
      case item => Future.successful(item)
    }
  }

  def addAgentReference(
    agentReference: AgentReference,
    correlationId: String
  ): Future[Boolean] = {
    coll.updateOne(
      Filters.equal("item.correlationId", correlationId),
      Updates.set("item.agentReference", agentReference.value)
    ).toFuture()
      .map(_.getModifiedCount > 0)
  }

  def markAsPermanentlyFailed(correlationId: String): Future[Boolean] = {
    coll.updateOne(
      Filters.equal("item.correlationId", correlationId),
      Updates.set("status", PermanentlyFailed)
    ).toFuture()
      .map(_.getModifiedCount > 0)
  }
