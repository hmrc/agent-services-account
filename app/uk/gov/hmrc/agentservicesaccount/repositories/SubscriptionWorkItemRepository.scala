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
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.Updates
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mongo.workitem.WorkItemFields
import uk.gov.hmrc.mongo.workitem.WorkItemRepository
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
