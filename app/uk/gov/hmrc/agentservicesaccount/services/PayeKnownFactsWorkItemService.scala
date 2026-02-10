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

package uk.gov.hmrc.agentservicesaccount.services

import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.{Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class PayeKnownFactsWorkItemService @Inject() (
  repository: SubscriptionWorkItemRepository
)(implicit
  ec: ExecutionContext
):

  def pullOutstanding(retryInterval: FiniteDuration): Future[Option[WorkItem[SubscriptionWorkItem]]] =
    val now = Instant.now()
    repository.pullOutstandingPaye(
      failedBefore = now.minus(Duration.ofMillis(retryInterval.toMillis)),
      availableBefore = now
    )

  def reschedule(workItem: WorkItem[SubscriptionWorkItem], retryInterval: FiniteDuration): Future[Boolean] =
    val nextRun = Instant.now().plus(Duration.ofMillis(retryInterval.toMillis))
    repository.markAs(workItem.id, ProcessingStatus.Failed, Some(nextRun))

  def complete(workItem: WorkItem[SubscriptionWorkItem]): Future[Boolean] =
    repository.complete(workItem.id, ProcessingStatus.Succeeded)

  def markManualIntervention(workItem: WorkItem[SubscriptionWorkItem]): Future[Boolean] =
    repository.complete(workItem.id, ProcessingStatus.PermanentlyFailed)
