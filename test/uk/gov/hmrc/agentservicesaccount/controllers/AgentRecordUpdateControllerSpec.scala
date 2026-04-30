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

package uk.gov.hmrc.agentservicesaccount.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.libs.json.{JsObject, Json}
import play.api.test.*
import play.api.test.Helpers.*
import uk.gov.hmrc.agentservicesaccount.auth.AuthActions
import uk.gov.hmrc.agentservicesaccount.helpers.TestConstants.*
import uk.gov.hmrc.agentservicesaccount.mocks.*
import uk.gov.hmrc.agentservicesaccount.models.HipAmendResponse
import uk.gov.hmrc.agentservicesaccount.models.HipAmendSuccess
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.internalauth.client.test.BackendAuthComponentsStub

import scala.concurrent.ExecutionContext

class AgentRecordUpdateControllerSpec
extends UnitSpec
with GuiceOneAppPerTest
with MockAppConfig
with MockAuthConnector
with MockAgentEntityService
with MockHipConnector
with MockInternalAuth
with MockDmsService
with MockFactory {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  val as: ActorSystem = ActorSystem()
  implicit val mat: Materializer = Materializer(as)

  val stubBackendAuthComponents: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(using stubControllerComponents(), implicitly)

  val mockAuthActions: AuthActions = AuthActions(mockAuthConnector, stubControllerComponents())

  val controller: AgentDetailsController = AgentDetailsController(
    stubControllerComponents(),
    mockAgentEntityService,
    mockHipConnector,
    mockDmsService,
    mockAuthActions,
    stubBackendAuthComponents
  )(using ec, mockAppConfig)

  val testHipAmendResponse: HipAmendResponse =
    HipAmendResponse(HipAmendSuccess("2024-07-15T09:30:47Z"))

  val amlsPayload: JsObject = Json.obj(
    "amlsDetails" -> Json.obj(
      "supervisoryBody" -> "SRA",
      "membershipNumber" -> "XAML00000123456",
      "evidenceObjectReference" -> "f28047ef-33f9-482e-a76a-ec4304de7b62"
    )
  )

  val agencyDetailsPayload: JsObject = Json.obj(
    "agencyDetails" -> Json.obj(
      "agencyName" -> "Test Agency",
      "agencyEmail" -> "test@example.com",
      "agencyTelephone" -> "07123456789",
      "agencyAddress" -> Json.obj(
        "addressLine1" -> "1 High Street",
        "postalCode" -> "TF1 1AA",
        "countryCode" -> "GB"
      )
    )
  )

  "agentRecordUpdate" should {
    "return OK for valid AMLS payload" in {
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))
      mockHipPutAgentRecord(testArn)(testHipAmendResponse)

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token", "Content-Type" -> "application/json")
          .withJsonBody(amlsPayload)
      )

      status(result) shouldBe OK
      (contentAsJson(result) \ "processingDate").as[String] shouldBe "2024-07-15T09:30:47Z"
    }

    "return OK for valid agency details payload" in {
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))
      mockHipPutAgentRecord(testArn)(testHipAmendResponse)

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token", "Content-Type" -> "application/json")
          .withJsonBody(agencyDetailsPayload)
      )

      status(result) shouldBe OK
      (contentAsJson(result) \ "processingDate").as[String] shouldBe "2024-07-15T09:30:47Z"
    }

    "return BadRequest when both sections are provided" in {
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))

      val bothPayload = Json.obj(
        "amlsDetails" -> Json.obj(
          "supervisoryBody" -> "SRA",
          "membershipNumber" -> "XAML00000123456"
        ),
        "agencyDetails" -> Json.obj(
          "agencyName" -> "Test Agency",
          "agencyEmail" -> "test@example.com"
        )
      )

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token", "Content-Type" -> "application/json")
          .withJsonBody(bothPayload)
      )

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_PAYLOAD"
      (contentAsJson(result) \ "message").as[String] should include("cannot both")
    }

    "return BadRequest when neither section is provided" in {
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token", "Content-Type" -> "application/json")
          .withJsonBody(Json.obj())
      )

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_PAYLOAD"
    }

    "return BadRequest for invalid JSON" in {
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token", "Content-Type" -> "application/json")
          .withJsonBody(Json.obj("amlsDetails" -> Json.obj("unknownField" -> "value")))
      )

      status(result) shouldBe BAD_REQUEST
    }

    "return BadRequest when no JSON body" in {
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token")
      )

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_PAYLOAD"
    }

    "return Unauthorized when no active session" in {
      import org.mockito.ArgumentMatchers.any
      import org.mockito.Mockito.when
      when(
        mockAuthConnector.authorise(any(), any())(using any(), any())
      ).thenReturn(scala.concurrent.Future.failed(
        new uk.gov.hmrc.auth.core.NoActiveSession("No session") {}
      ))

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders("Content-Type" -> "application/json")
          .withJsonBody(amlsPayload)
      )

      status(result) shouldBe UNAUTHORIZED
    }

    "return Forbidden when agent has no HMRC-AS-AGENT enrolment" in {
      mockAuth()(Right(enrolmentsWithoutIrSAAgent))

      val result = controller.agentRecordUpdate.apply(
        FakeRequest(PUT, "/agent-record-update")
          .withHeaders(HeaderNames.authorisation -> "Bearer token", "Content-Type" -> "application/json")
          .withJsonBody(amlsPayload)
      )

      status(result) shouldBe FORBIDDEN
    }

  }
}
