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
import uk.gov.hmrc.agentservicesaccount.config.PayeKnownFactsJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionId}
import uk.gov.hmrc.mongo.workitem.WorkItem

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class PayeKnownFactsWorker @Inject() (
  workItemService: PayeKnownFactsWorkItemService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  jobConfig: PayeKnownFactsJobConfig
)(using
  ec: ExecutionContext
) extends Logging:

  def runOnce(): Future[Unit] =
    workItemService.pullOutstanding(jobConfig.retryInterval).flatMap {
      case None => Future.unit
      case Some(workItem) =>
        process(workItem).recoverWith { case NonFatal(error) =>
          logger.warn(s"Paye known facts processing failed for work item ${workItem.id}", error)
          handleFailure(workItem)
        }
    }

  private def process(workItem: WorkItem[SubscriptionWorkItem]): Future[Unit] =
    workItem.item.agentReference match {
      case None =>
        logger.warn(s"Paye known facts work item missing agent reference: ${workItem.id}")
        handleFailure(workItem)
      case Some(agentReference) =>
        if shouldStopRetrying(workItem) then
          logger.warn(s"Paye known facts work item reached max attempts: ${workItem.id}")
          workItemService.markManualIntervention(workItem).map(_ => ())
        else
          // Local stubs expect auth/session headers; QA/Prod use internal auth and leave these empty.
          given HeaderCarrier = HeaderCarrier(
            authorization = workItem.item.bearerToken.map(Authorization.apply),
            sessionId = workItem.item.sessionId.map(SessionId.apply)
          )
          enrolmentStoreProxyConnector.queryKnownFactsForAgent(LegacyRegime.PAYE, agentReference.value).flatMap {
            case None =>
              logger.info(s"Paye known facts not available yet for work item: ${workItem.id}")
              handleFailure(workItem)
            case Some(_) =>
              (workItem.item.groupId, workItem.item.adminCredId) match
                case (Some(groupId), Some(adminCredId)) =>
                  enrolmentStoreProxyConnector
                    .allocateAgentEnrolment(
                      regime = LegacyRegime.PAYE,
                      groupId = groupId,
                      agentReference = agentReference.value,
                      adminCredId = adminCredId
                    )
                    .flatMap { _ =>
                      logger.info(s"Paye known facts allocation completed for work item: ${workItem.id}")
                      workItemService.complete(workItem).map(_ => ())
                    }
                case _ =>
                  logger.warn(s"Paye known facts work item missing group or admin cred ID: ${workItem.id}")
                  workItemService.markManualIntervention(workItem).map(_ => ())
          }
    }

  private def handleFailure(workItem: WorkItem[SubscriptionWorkItem]): Future[Unit] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then
      workItemService.markManualIntervention(workItem).map(_ => ())
    else
      workItemService.reschedule(workItem, jobConfig.retryInterval).map(_ => ())

  private def shouldStopRetrying(workItem: WorkItem[SubscriptionWorkItem]): Boolean =
    workItem.failureCount >= jobConfig.maxAttempts
