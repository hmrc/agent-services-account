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
import play.api.libs.json.Json
import play.api.libs.json.JsObject
import uk.gov.hmrc.agentservicesaccount.connectors.RoboticsInvocationConnector
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.Operation
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.RequestId
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsInvocationRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.SaSubscriptionRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class SaRoboticsWorker @Inject() (
  workItemService: SaRoboticsWorkItemService,
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

  def runOnce(now: Instant = Instant.now()): Future[Unit] = workItemService.pullOutstanding(now).flatMap {
    case None => Future.unit
    case Some(workItem) =>
      process(workItem, now).recoverWith { case NonFatal(error) =>
        logger.warn(s"SA robotics invocation failed for work item ${workItem.id}", error)
        // Guard against overwriting a successful callback transition (agentReference set + status moved back to ToDo)
        // if the invoke failed/timed out locally after the upstream had already accepted the request.
        workItemService.markDeferred(workItem).map(_ => ())
      }
  }

  private def process(
    workItem: WorkItem[SubscriptionWorkItem],
    now: Instant
  ): Future[Unit] =
    workItem.item.regime match
      case LegacyRegime.SA =>
        workItem.item.subscriptionRequest match
          case request: SaSubscriptionRequest =>
            val postcode = request.address.postCode.getOrElse("")
            if !request.isAbroad && postcode.isEmpty then
              // Should be prevented by request validation, but keep as a guard for any existing stored data.
              throw new RuntimeException("Postcode missing for UK SA robotics invocation")
            if request.isAbroad && postcode.isEmpty then
              // For abroad requests postcode is optional, but the robotics stub expects a postcode field.
              // We pass an empty string until the final robotics contract is confirmed for overseas addresses.
              logger.info(s"SA robotics invocation for abroad request ${workItem.item.requestId} has no postcode")

            val operationData: JsObject =
              if appConfig.stubsCompatibilityMode then
                // Stub compatibility: agents-external-stubs validates the flat operation data shape.
                Json.obj(
                  "requestId" -> workItem.item.requestId,
                  "targetSystem" -> TargetSystem.CESA.toString,
                  "postcode" -> postcode,
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
                  else addressBase ++ Json.obj("postcode" -> postcode)

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
              // Persist a marker so we don't re-invoke this work item once it's "in flight" and awaiting callback.
              // We only set this after a successful outbound call to keep crash-recovery behaviour for items that were
              // claimed (InProgress) but never actually invoked.
              .flatMap(_ => workItemService.markInvoked(workItem, invokedAt = now).map(_ => ()))
          case other => Future.failed(new RuntimeException(s"Unexpected subscription request type for SA robotics invocation: ${other.getClass.getName}"))
      case other => Future.failed(new RuntimeException(s"Unexpected regime in SA robotics worker: $other"))
