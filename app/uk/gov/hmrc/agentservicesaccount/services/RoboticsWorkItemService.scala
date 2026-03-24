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

import org.apache.pekko.Done
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@Singleton
class RoboticsWorkItemService @Inject() (
  repository: SubscriptionWorkItemRepository
)(using ExecutionContext):

  def pullOutstanding(
    regime: LegacyRegime,
    retryInterval: FiniteDuration
  ): Future[Option[WorkItem[SubscriptionWorkItem]]] =
    val now = Instant.now()
    repository.pullAwaitingRobotics(
      regime = regime,
      failedBefore = now.minus(Duration.ofMillis(retryInterval.toMillis)),
      availableBefore = now
    )

  def markFailed(workItem: WorkItem[SubscriptionWorkItem]): Future[Done] = repository
    .markAs(workItem.id, ProcessingStatus.Failed)
    .map(_ => Done)

  def markAsInvoked(workItem: WorkItem[SubscriptionWorkItem]): Future[Done] = repository
    .markAsInvoked(workItem.id)
    .map(_ => Done)

  def markPermanentlyFailed(workItem: WorkItem[SubscriptionWorkItem]): Future[Done] = repository
    .complete(workItem.id, ProcessingStatus.PermanentlyFailed)
    .map(_ => Done)
