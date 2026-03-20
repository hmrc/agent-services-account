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

import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.config.KnownFactsJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.mongo.workitem.WorkItem

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class KnownFactsWorker @Inject() (
  workItemService: KnownFactsWorkItemService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector
)(using ec: ExecutionContext)
extends Logging:

  def runOnce(using
    jobConfig: KnownFactsJobConfig,
    regime: LegacyRegime
  ): Future[Unit] = workItemService.pullOutstanding(regime, jobConfig.retryInterval).flatMap {
    case None => Future.unit
    case Some(workItem) =>
      process(workItem).recoverWith { case NonFatal(error) =>
        logger.warn(s"$regime known facts failed for work item ${workItem.id}", error)
        handleFailure(workItem)
      }
  }

  private def process(workItem: WorkItem[SubscriptionWorkItem])(using
    jobConfig: KnownFactsJobConfig,
    regime: LegacyRegime
  ): Future[Unit] =
    workItem.item.agentReference match {
      case None =>
        logger.warn(s"$regime work item missing agent reference: ${workItem.id}" +
          s"(this should not be possible as the mongo query requires an agent reference to be present)")
        handleFailure(workItem)
      case Some(agentReference) =>
        if shouldStopRetrying(workItem) then
          logger.warn(s"$regime known facts work item reached max attempts: ${workItem.id}")
          workItemService.markManualIntervention(workItem).map(_ => ())
        else
          // Local stubs expect auth/session headers; QA/Prod use internal auth and leave these empty.
          given HeaderCarrier = HeaderCarrier(
            authorization = workItem.item.bearerToken.map(Authorization.apply),
            sessionId = workItem.item.sessionId.map(SessionId.apply)
          )
          enrolmentStoreProxyConnector.queryKnownFactsForAgent(regime, agentReference.value).flatMap {
            case None =>
              logger.info(s"$regime known facts not available yet for work item: ${workItem.id}")
              handleFailure(workItem)
            case Some(_) =>
              enrolmentStoreProxyConnector
                .allocateAgentEnrolment(
                  regime = regime,
                  groupId = workItem.item.groupId,
                  agentReference = agentReference.value,
                  adminCredId = workItem.item.adminCredId
                )
                .flatMap { _ =>
                  logger.info(s"$regime enrolment allocated for work item: ${workItem.id}")
                  workItemService.complete(workItem).map(_ => ())
                }
          }
    }

  private def handleFailure(workItem: WorkItem[SubscriptionWorkItem])(using jobConfig: KnownFactsJobConfig): Future[Unit] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then
      workItemService.markManualIntervention(workItem).map(_ => ())
    else
      workItemService.reschedule(workItem, jobConfig.retryInterval).map(_ => ())

  private def shouldStopRetrying(workItem: WorkItem[SubscriptionWorkItem])(using jobConfig: KnownFactsJobConfig): Boolean =
    workItem.failureCount >= jobConfig.maxAttempts
