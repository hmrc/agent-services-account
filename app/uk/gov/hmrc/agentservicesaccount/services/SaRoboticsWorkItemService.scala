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

import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class SaRoboticsWorkItemService @Inject() (
  repository: SubscriptionWorkItemRepository
)(using
  ec: ExecutionContext
):

  def pullOutstanding(now: Instant): Future[Option[WorkItem[SubscriptionWorkItem]]] = repository.pullOutstandingRobotics(
    regime = LegacyRegime.SA,
    availableBefore = now
  )

  def markDeferred(workItem: WorkItem[SubscriptionWorkItem]): Future[Boolean] = repository.markAsDeferredIfStillAwaitingInvocation(workItem.id)

  def markInvoked(
    workItem: WorkItem[SubscriptionWorkItem],
    invokedAt: Instant
  ): Future[Boolean] = repository.markRoboticsInvoked(workItem.id, invokedAt = invokedAt)
