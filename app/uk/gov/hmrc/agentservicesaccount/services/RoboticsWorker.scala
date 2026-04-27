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
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentservicesaccount.config.{AppConfig, WorkItemJobConfig}
import uk.gov.hmrc.agentservicesaccount.connectors.RoboticsInvocationConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.{CorrelationId, RequestId}
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionId}
import uk.gov.hmrc.mongo.workitem.WorkItem

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class RoboticsWorker @Inject() (
  workItemService: RoboticsWorkItemService,
  roboticsInvocationConnector: RoboticsInvocationConnector,
  appConfig: AppConfig
)(using
  ec: ExecutionContext
)
extends Logging:

  // Robotics OpenAPI spec requires `entityType` (e.g. Sole Trader / Partnership / Limited Company).
  // However, APB-10568 does not introduce capture/validation of entity type in the inbound SA request yet (see story N1),
  // and the field spec marks the source as TBC. For now, we send "Sole Trader" as a placeholder, to be replaced once
  // entity type is captured and the contract is finalised.
  private val defaultEntityType: String = "Sole Trader"

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
        logger.error(s"[RoboticsWorker] ${regime.toString} robotics invocation failed for work item ${workItem.id}", error)
        // TODO it is unclear how robotics handles duplicate requests that can happen if we receive an error/timeout response but they process the request successfully.
        //  When HIP contract is finalised we should address the possibility of this happening as currently we may continue retrying
        //  Which could either generate multiple robotics cases or fail the work item permanently if we exceed max attempts.
        handleFailure(workItem)
      }
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

    def createRoboticsRequestBodyForStubs() = {

      val roboticsArgumentValueForStubs = RoboticsArgumentValueForStubs(
        requestId = workItem.item.requestId,
        targetSystem = targetSystem.toString,
        postcode = request.address.postCode,
        operationRequired = Operation.CREATE.toString,
      )

      val roboticsArgumentForStubs = RoboticsArgumentForStubs(
        argumentType = "string",
        argumentValue = roboticsArgumentValueForStubs
      )

      val roboticsWorkflowDataForStubs = RoboticsWorkflowDataForStubs(
        arguments = List(roboticsArgumentForStubs)
      )

      val roboticsRequestDataForStubs = RoboticsRequestDataForStubs(
        workflowData = roboticsWorkflowDataForStubs
      )

      val roboticsRequestForStubs = RoboticsRequestForStubs(
        requestData = List(roboticsRequestDataForStubs)
      )

      Json.toJson(roboticsRequestForStubs).as[JsObject]
    }

    def createRoboticsRequestBodyForAllEnvironments() = {

      val roboticsArgumentValue = RoboticsArgumentValue(
        requestId = workItem.item.requestId,
        targetSystem = targetSystem.toString,
        operationRequired = Operation.CREATE.toString,
        entityType = defaultEntityType,
        agentName = request.agentName,
        tradingAs = request.agentName,
        isAbroad = request.isAbroad,
        addressLine1 = request.address.line1,
        addressLine2 = request.address.line2,
        addressLine3 = request.address.line3,
        addressLine4 = request.address.line4,
        postcode = request.address.postCode,
        phone = request.phoneNumber,
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

    val operationData: JsObject = {

      if appConfig.stubsCompatibilityMode then
        // TODO update stubs to accept the same contract as QA/Prod, then remove this branch. Abroad currently not supported by the stub
        createRoboticsRequestBodyForStubs()
      else
        createRoboticsRequestBodyForAllEnvironments()
      end if

    }

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
        logger.info(s"[RoboticsWorker] Robotics invoked for $regime, work item: ${workItem.id}")
        workItemService.markAsInvoked(workItem)
      }
  }

  def handleFailure(workItem: WorkItem[SubscriptionWorkItem])(using jobConfig: WorkItemJobConfig): Future[Done] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then
      workItemService.markPermanentlyFailed(workItem)
    else
      workItemService.markFailed(workItem)
