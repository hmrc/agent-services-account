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
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import uk.gov.hmrc.agentservicesaccount.connectors.RoboticsInvocationConnector
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.Operation
import uk.gov.hmrc.agentservicesaccount.models.subscription.UsesRobotics
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsInvocationRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.RequestId
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
  appConfig: AppConfig
)(using
  ec: ExecutionContext
)
extends Logging:

  private val schemaVersion: Int = 1
  // Robotics OpenAPI spec requires `entityType` (e.g. Sole Trader / Partnership / Limited Company).
  // However, APB-10568 does not introduce capture/validation of entity type in the inbound SA request yet (see story N1),
  // and the field spec marks the source as TBC. For now we send "Sole Trader" as a placeholder, to be replaced once
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
        handleFailure(workItem)
      }
  }

  private def process(
    workItem: WorkItem[SubscriptionWorkItem]
  )(using
    jobConfig: WorkItemJobConfig,
    regime: LegacyRegime & UsesRobotics
  ): Future[Done] = {
    val request = workItem.item.subscriptionRequest
    val targetSystem =
      regime match {
        case LegacyRegime.SA => TargetSystem.CESA
        case LegacyRegime.CT => TargetSystem.COTAX
      }

    val operationData: JsObject =
      if appConfig.stubsCompatibilityMode then
        // TODO update stubs to accept the same contract as QA/Prod, then remove this branch. Abroad currently not supported by the stub
        Json.obj(
          "requestId" -> workItem.item.requestId,
          "targetSystem" -> targetSystem.toString,
          "postcode" -> request.address.postCode,
          "operationRequired" -> Operation.CREATE.toString
        )
      else
        // HIP/robotics contract: nested agent details.
        val addressBase = Json.obj(
          "line1" -> request.address.line1,
          "line2" -> request.address.line2,
          "line3" -> request.address.line3,
          "line4" -> request.address.line4
        )
        val address =
          if request.isAbroad then addressBase
          else addressBase ++ Json.obj("postcode" -> request.address.postCode)

        val contact = Json.obj(
          "phone" -> request.phoneNumber,
          "email" -> request.emailAddress
        )

        val agentDetails = Json.obj(
          "agentName" -> request.agentName,
          "isAbroad" -> request.isAbroad,
          "address" -> address,
          "contact" -> contact
        )

        Json.obj(
          "schemaVersion" -> schemaVersion,
          "requestId" -> workItem.item.requestId,
          "targetSystem" -> TargetSystem.CESA.toString,
          "operationRequired" -> Operation.CREATE.toString,
          "entityType" -> defaultEntityType,
          "agentDetails" -> agentDetails
        )

    val payload = Json.toJsObject(RoboticsInvocationRequest.fromOperationData(Json.stringify(operationData)))

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
