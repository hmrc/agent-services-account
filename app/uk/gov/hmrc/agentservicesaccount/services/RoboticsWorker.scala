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
import play.api.Logging
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.RoboticsInvocationConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.RequestId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
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
class RoboticsWorker @Inject() (
  workItemService: RoboticsWorkItemService,
  roboticsInvocationConnector: RoboticsInvocationConnector,
  legacySubscriptionAuditService: LegacySubscriptionAuditService,
  legacySubscriptionEmailService: LegacySubscriptionEmailService,
  appConfig: AppConfig
)(using
  ec: ExecutionContext
)
extends Logging:

  def runOnce(using
    jobConfig: WorkItemJobConfig,
    regime: LegacyRegime & UsesRobotics
  ): Future[Done] = workItemService.pullOutstanding(
    regime,
    jobConfig.retryInterval
  ).flatMap {
    case None => Future.successful(Done)
    case Some(workItem) =>
      process(workItem).recoverWith { case NonFatal(error) =>
        logger.error(s"[RoboticsWorker] ${regime.toString} robotics invocation failed for work item ${workItem.item.requestId}", error)
        // TODO it is unclear how robotics handles duplicate requests that can happen if we receive an error/timeout response but they process the request successfully.
        //  When HIP contract is finalised we should address the possibility of this happening as currently we may continue retrying
        //  Which could either generate multiple robotics cases or fail the work item permanently if we exceed max attempts.
        handleFailure(workItem)
      }
  }

  private def createRoboticsRequestBodyForAllEnvironments(
    workItem: WorkItem[SubscriptionWorkItem],
    targetSystem: TargetSystem,
    request: SubscriptionRequest
  ) = {

    val roboticsArgumentValue = RoboticsArgumentValue(
      requestId = workItem.item.requestId,
      targetSystem = targetSystem.toString,
      operationRequired = Operation.CREATE.toString,
      entityType = workItem.item.entityType,
      agentName = request.agentName,
      tradingAs = request.agentName,
      isAbroad = request.isAbroad,
      addressLine1 = request.address.line1,
      addressLine2 = request.address.line2,
      addressLine3 = request.address.line3,
      addressLine4 = request.address.line4,
      postcode = request.address.postCode,
      phone = request.phoneNumber,
      email = request.emailAddress,
      ARN = workItem.item.arn.value
    )

    val roboticsArgument = RoboticsArgument(
      argumentType = "string",
      argumentValue = roboticsArgumentValue
    )

    val roboticsWorkflowData = RoboticsWorkflowData(
      arguments = List(roboticsArgument)
    )

    val roboticsWorkflowMetaData = RoboticsWorkflowMetaData(
      solution = appConfig.roboticsWorkflowMetaDataSolution,
      workflowId = appConfig.roboticsWorkflowMetaDataWorkflowID
    )

    val roboticsRequestData = RoboticsRequestData(
      workflowMetaData = roboticsWorkflowMetaData,
      workflowData = roboticsWorkflowData
    )

    val roboticsRequestMetaData = RoboticsRequestMetaData(
      initiatorType = "THIRD_PARTY_APP",
      initiatorId = "ASA",
      externalInvokerReqId = workItem.item.requestId
    )

    val roboticsRequest = RoboticsRequest(
      requestMetaData = roboticsRequestMetaData,
      requestData = List(roboticsRequestData)
    )

    Json.toJson(roboticsRequest).as[JsObject]
  }

  private def process(
    workItem: WorkItem[SubscriptionWorkItem]
  )(using
    regime: LegacyRegime & UsesRobotics
  ): Future[Done] = {
    val request = workItem.item.subscriptionRequest
    val targetSystem =
      regime match {
        case LegacyRegime.SA => TargetSystem.CESA
        case LegacyRegime.CT => TargetSystem.COTAX
      }

    val operationData: JsObject = createRoboticsRequestBodyForAllEnvironments(
      workItem,
      targetSystem,
      request
    )

    val payload = operationData

    given HeaderCarrier =
      if appConfig.stubsCompatibilityMode then
        // Local stubs expect auth/session headers.
        HeaderCarrier(
          authorization = workItem.item.bearerToken.map(Authorization.apply),
          sessionId = workItem.item.sessionId.map(SessionId.apply)
        )
      else
        // QA/Prod path does not replay end-user auth/session on robotics invoke.
        HeaderCarrier()
    val requestId = RequestId(workItem.item.requestId)
    val correlationId = CorrelationId.fromRequestId(requestId)

    roboticsInvocationConnector
      .invoke(payload, correlationId = correlationId)
      .flatMap { _ =>
        logger.info(s"[RoboticsWorker] Robotics invoked for $regime, work item: ${workItem.item.requestId}")
        workItemService.markAsInvoked(workItem)
      }
  }

  def handleFailure(workItem: WorkItem[SubscriptionWorkItem])(using jobConfig: WorkItemJobConfig): Future[Done] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then {
      for {
        _ <- legacySubscriptionAuditService.auditFailure(
          arn = workItem.item.arn,
          regime = workItem.item.regime,
          failureReason = "Max retry attempts reached in RoboticsWorker"
        ).recover { case _ => () }
        _ <- legacySubscriptionEmailService.sendFailureEmailIgnoreErrors(workItem.item)
        result <- workItemService.markPermanentlyFailed(workItem)
      } yield result
    }
    else
      workItemService.markFailed(workItem)
