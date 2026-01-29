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
import com.typesafe.config.Config
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.mongo.workitem.WorkItemFields
import uk.gov.hmrc.mongo.workitem.WorkItemRepository
import uk.gov.hmrc.mongo.MongoComponent

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
  workItemFields = WorkItemFields.default
):

  override lazy val requiresTtlIndex = false

  override def now(): Instant = Instant.now()

  override def inProgressRetryAfter: Duration = config.getDuration("work-item-repository.subscriptions.retry-in-progress-after")
