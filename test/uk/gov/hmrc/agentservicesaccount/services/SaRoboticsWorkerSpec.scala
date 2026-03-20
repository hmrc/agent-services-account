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

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.RoboticsInvocationConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SaRoboticsWorkerSpec
extends UnitSpec
with BeforeAndAfterEach:

  private val workItemService = mock[SaRoboticsWorkItemService]
  private val connector = mock[RoboticsInvocationConnector]
  private val appConfig = mock[AppConfig]

  private val worker =
    new SaRoboticsWorker(
      workItemService,
      connector,
      appConfig
    )

  private val ukRequest = SaSubscriptionRequest(
    agentName = "Agent Name",
    contactName = "Contact Name",
    phoneNumber = Some("01234567890"),
    emailAddress = Some("agent@example.com"),
    address = SubscriptionAddress(
      line1 = "1 High Street",
      line2 = "Town",
      line3 = Some("Region"),
      line4 = Some("Country"),
      postCode = Some("AA1 1AA")
    ),
    isAbroad = false
  )

  private val abroadRequestNoPostcode = ukRequest.copy(
    isAbroad = true,
    address = ukRequest.address.copy(postCode = None)
  )

  private def buildWorkItem(
    request: SaSubscriptionRequest,
    requestId: String = "req-123",
    failureCount: Int = 0
  ): WorkItem[SubscriptionWorkItem] = WorkItem(
    id = new ObjectId(),
    receivedAt = Instant.now(),
    updatedAt = Instant.now(),
    availableAt = Instant.now(),
    status = InProgress,
    failureCount = failureCount,
    item = SubscriptionWorkItem(
      arn = Arn("TARN0000001"),
      subscriptionRequest = request,
      regime = LegacyRegime.SA,
      agentReference = None,
      requestId = requestId,
      sessionId = Some("session-123"),
      bearerToken = Some("Bearer test-token")
    )
  )

  "SaRoboticsWorker" should {
    "do nothing when there is no outstanding work item" in {
      val maxAttempts = 3
      val now = Instant.parse("2026-02-26T10:00:00Z")
      when(workItemService.pullOutstanding(now)).thenReturn(Future.successful(None))

      worker.runOnce(maxAttempts, now).futureValue

      verifyNoInteractions(connector)
    }

    "invoke robotics with stub-compatible payload and replay auth/session headers in stubs mode" in {
      val now = Instant.parse("2026-02-26T10:00:00Z")
      val maxAttempts = 3
      val workItem = buildWorkItem(ukRequest, requestId = "stub-req-123")
      val expectedOperationData = Json.obj(
        "requestId" -> "stub-req-123",
        "targetSystem" -> TargetSystem.CESA.toString,
        "postcode" -> "AA1 1AA",
        "operationRequired" -> Operation.CREATE.toString
      )
      val expectedPayload: JsObject = Json.toJsObject(RoboticsInvocationRequest.fromOperationData(Json.stringify(expectedOperationData)))
      val hcCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      when(appConfig.stubsCompatibilityMode).thenReturn(true)
      when(workItemService.pullOutstanding(now)).thenReturn(Future.successful(Some(workItem)))
      when(connector.invoke(eqTo(expectedPayload), any[CorrelationId])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(workItemService.saveToDatabase(
        workItem.id,
        Succeeded,
        workItem.failureCount,
        now
      )).thenReturn(Future.successful(true))

      worker.runOnce(maxAttempts, now).futureValue

      verify(connector).invoke(eqTo(expectedPayload), any[CorrelationId])(using hcCaptor.capture())
      verify(workItemService).saveToDatabase(
        workItem.id,
        Succeeded,
        workItem.failureCount,
        now
      )

      hcCaptor.getValue.authorization.map(_.value) shouldBe Some("Bearer test-token")
      hcCaptor.getValue.sessionId.map(_.value) shouldBe Some("session-123")
    }

    "invoke robotics with HIP payload shape for abroad requests in non-stub mode" in {
      val now = Instant.parse("2026-02-26T10:00:00Z")
      val maxAttempts = 3
      val workItem = buildWorkItem(abroadRequestNoPostcode, requestId = "hip-req-123")
      val expectedOperationData = Json.obj(
        "schemaVersion" -> 1,
        "requestId" -> "hip-req-123",
        "targetSystem" -> TargetSystem.CESA.toString,
        "operationRequired" -> Operation.CREATE.toString,
        "entityType" -> "Sole Trader",
        "agentDetails" -> Json.obj(
          "agentName" -> abroadRequestNoPostcode.agentName,
          "isAbroad" -> true,
          "address" -> Json.obj(
            "line1" -> abroadRequestNoPostcode.address.line1,
            "line2" -> abroadRequestNoPostcode.address.line2,
            "line3" -> abroadRequestNoPostcode.address.line3,
            "line4" -> abroadRequestNoPostcode.address.line4
          ),
          "contact" -> Json.obj(
            "phone" -> abroadRequestNoPostcode.phoneNumber,
            "email" -> abroadRequestNoPostcode.emailAddress
          )
        )
      )
      val expectedPayload: JsObject = Json.toJsObject(RoboticsInvocationRequest.fromOperationData(Json.stringify(expectedOperationData)))
      val hcCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      when(appConfig.stubsCompatibilityMode).thenReturn(false)
      when(workItemService.pullOutstanding(now)).thenReturn(Future.successful(Some(workItem)))
      when(connector.invoke(eqTo(expectedPayload), any[CorrelationId])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(workItemService.saveToDatabase(
        workItem.id,
        Succeeded,
        workItem.failureCount,
        now
      )).thenReturn(Future.successful(true))

      worker.runOnce(maxAttempts, now).futureValue

      verify(connector).invoke(eqTo(expectedPayload), any[CorrelationId])(using hcCaptor.capture())
      verify(workItemService).saveToDatabase(
        workItem.id,
        Succeeded,
        workItem.failureCount,
        now
      )

      hcCaptor.getValue.authorization shouldBe None
      hcCaptor.getValue.sessionId shouldBe None
    }

    "mark the work item deferred when invocation fails" in {
      val now = Instant.parse("2026-02-26T10:00:00Z")
      val maxAttempts = 3
      val workItem = buildWorkItem(ukRequest, requestId = "failed-req-123")

      when(appConfig.stubsCompatibilityMode).thenReturn(false)
      when(workItemService.pullOutstanding(now)).thenReturn(Future.successful(Some(workItem)))
      when(connector.invoke(any[JsObject], any[CorrelationId])(using any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("boom")))
      when(workItemService.markDeferred(workItem)).thenReturn(Future.successful(true))

      worker.runOnce(maxAttempts, now).futureValue

      verify(workItemService).markDeferred(workItem)
      verify(workItemService, never()).saveToDatabase(
        workItem.id,
        Failed,
        workItem.failureCount,
        now
      )
    }

    "mark the work item PermanentlyFailed when workItem reaches maximum retries" in {
      val now = Instant.parse("2026-02-26T10:00:00Z")
      val maxAttempts = 3
      val workItem = buildWorkItem(
        ukRequest,
        requestId = "failed-req-123",
        maxAttempts - 1
      )

      when(appConfig.stubsCompatibilityMode).thenReturn(false)
      when(workItemService.pullOutstanding(now)).thenReturn(Future.successful(Some(workItem)))
      when(connector.invoke(any[JsObject], any[CorrelationId])(using any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("boom")))
      when(workItemService.markDeferred(workItem)).thenReturn(Future.successful(true))
      when(workItemService.saveToDatabase(
        workItem.id,
        PermanentlyFailed,
        maxAttempts,
        now
      )).thenReturn(Future.successful(true))

      worker.runOnce(maxAttempts, now).futureValue

      verify(workItemService).saveToDatabase(
        workItem.id,
        PermanentlyFailed,
        maxAttempts,
        now
      )
    }
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(
      workItemService,
      connector,
      appConfig
    )
