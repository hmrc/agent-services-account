/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.test.*
import play.api.test.Helpers.*
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentservicesaccount.auth.AuthActions
import uk.gov.hmrc.agentservicesaccount.helpers.TestConstants.*
import uk.gov.hmrc.agentservicesaccount.mocks.*
import uk.gov.hmrc.agentservicesaccount.models.agententity.{EntityCheckException, EntityCheckResult}
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.internalauth.client.test.BackendAuthComponentsStub

import scala.concurrent.ExecutionContext

class AgentDetailsControllerSpec
extends UnitSpec
with GuiceOneAppPerTest
with MockAppConfig
with MockAuthConnector
with MockAgentEntityService
  with MockDesConnector
  with MockInternalAuth
  with MockDmsService
with MockFactory {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  val as: ActorSystem = ActorSystem()
  implicit val mat: Materializer = Materializer(as)
  
  val stubBackendAuthComponents: BackendAuthComponents = BackendAuthComponentsStub(mockStubBehaviour)(stubControllerComponents(), implicitly)
  
  val mockAuthActions: AuthActions = new AuthActions(mockAuthConnector, stubControllerComponents())

  val controller =
    new AgentDetailsController(
      stubControllerComponents(),
      mockAgentEntityService,
      mockDmsService,
      mockAuthActions,
      stubBackendAuthComponents
    )(ec, mockAppConfig)

  "agentVerifyEntity" should {
    "return OK" when {
      "not suspended and a GET request to /agent-record-with-checks" in {

        mockAuth()(Right(enrolmentsWithNoIrSAAgent))
        mockVerifyEntitySuccess(testArn)(EntityCheckResult(testAgentDetailsDesResponse, Seq.empty[EntityCheckException]))

        val result = controller
          .agentGetWithChecks
          .apply(
            FakeRequest(GET, "/agent-record-with-checks")
              .withHeaders(HeaderNames.authorisation -> "Some auth token")
          )

        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(testAgentDetailsDesResponse)

      }
    }

    "suspended and a GET request to /agent-record-with-checks" in {
      val agentDetailsDesResponse = testAgentDetailsDesResponse.copy(suspensionDetails = Some(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))))
      
      mockAuth()(Right(enrolmentsWithNoIrSAAgent))
      
      mockVerifyEntitySuccess(testArn)(
        EntityCheckResult(
          agentDetailsDesResponse,
          Seq.empty[EntityCheckException]
        )
      )

      val result = controller
        .agentGetWithChecks
        .apply(
          FakeRequest(GET, "/agent-record-with-checks")
            .withHeaders(HeaderNames.authorisation -> "Some auth token")
        )

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(agentDetailsDesResponse)

    }

  }

  "clientVerifyEntity" should {
    "return OK" when {
      "not suspended and a GET request to /agent-record-with-checks/arn/:arn" in {
        mockInternalAuthSuccess()
        mockVerifyEntitySuccess(testArn)(EntityCheckResult(testAgentDetailsDesResponse, Seq.empty[EntityCheckException]))

        val result = controller
          .clientGetWithChecks(testArn)
          .apply(
            FakeRequest(POST, s"/agent-record-with-checks/arn/$testArn")
              .withHeaders(HeaderNames.authorisation -> "Some auth token", "Content-Type" -> "application/json")
          )

        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(testAgentDetailsDesResponse)

      }

      "suspended and a GET request to/agent-record-with-checks/arn/:arn" in {
        val agentDetailsDesResponse = testAgentDetailsDesResponse.copy(suspensionDetails = Some(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))))

        mockInternalAuthSuccess()
        mockVerifyEntitySuccess(testArn)(EntityCheckResult(agentDetailsDesResponse, Seq.empty[EntityCheckException]))

        val result = controller
          .clientGetWithChecks(testArn)
          .apply(
            FakeRequest(GET, s"/agent-record-with-checks/arn/$testArn\"")
              .withHeaders(HeaderNames.authorisation -> "Some auth token", "Content-Type" -> "application/json")
          )

        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(agentDetailsDesResponse)

      }
    }
  }
}

