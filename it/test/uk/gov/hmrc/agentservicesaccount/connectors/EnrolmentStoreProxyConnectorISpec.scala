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

package uk.gov.hmrc.agentservicesaccount.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlEqualTo, verify as verifyWiremock}
import org.scalatest.exceptions.TestFailedException
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.{CT, PAYE, SA}
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayePostcode
import uk.gov.hmrc.agentservicesaccount.models.{Enrolment, Es20Enrolment, Es20Response, GroupId}
import uk.gov.hmrc.agentservicesaccount.stubs.EnrolmentStoreProxyStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.http.HeaderCarrier

class EnrolmentStoreProxyConnectorISpec
extends ComponentSpecHelper
with EnrolmentStoreProxyStubs {

  private given Request[AnyContentAsEmpty.type] = FakeRequest()
  private given HeaderCarrier = HeaderCarrier()

  lazy val connector: EnrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]

  val testGroupId = GroupId("test-group-id")

  "ES3" should {
    "return active principal enrolments on a successful 200 response" in {
      givenEs3CallSucceeds(testGroupId)(SA, CT, PAYE)

      val result = connector.queryEnrolmentsAllocatedToGroup(testGroupId).futureValue

      result shouldBe Seq(
        Enrolment(service = SA.enrolmentKey, state = "Activated", identifiers = Seq.empty),
        Enrolment(service = CT.enrolmentKey, state = "Activated", identifiers = Seq.empty),
        Enrolment(service = PAYE.enrolmentKey, state = "Activated", identifiers = Seq.empty)
      )
    }

    "return nothing on a successful 404 response" in {
      givenEs3CallSucceeds(testGroupId)()

      val result = connector.queryEnrolmentsAllocatedToGroup(testGroupId).futureValue

      result shouldBe Nil
    }

    "throw error when EACD returns unexpected response" in {
      givenEs3CallFails(testGroupId)

      intercept[TestFailedException](connector.queryEnrolmentsAllocatedToGroup(testGroupId).futureValue)
    }
  }

  "ES20" should {
    "send agent reference and postcode for PAYE" in {
      val response = Es20Response(PAYE.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))
      givenEs20CallSucceeds(
        expectedBody = Json.obj(
          "service" -> PAYE.enrolmentKey,
          "knownFacts" -> Json.arr(
            Json.obj("key" -> "IRAgentReference", "value" -> "A12345"),
            Json.obj("key" -> "IRAgentPostcode", "value" -> "AA1 1AA")
          )
        ).toString,
        response = response
      )

      connector.queryKnownFactsForAgent(PAYE, "A12345", PayePostcode.from(Some("AA1 1AA"))).futureValue shouldBe Some(response)
    }

    "send only agent reference for SA" in {
      val response = Es20Response(SA.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))
      givenEs20CallSucceeds(
        expectedBody = Json.obj(
          "service" -> SA.enrolmentKey,
          "knownFacts" -> Json.arr(
            Json.obj("key" -> "IRAgentReference", "value" -> "A12345")
          )
        ).toString,
        response = response
      )

      connector.queryKnownFactsForAgent(SA, "A12345", None).futureValue shouldBe Some(response)
    }

    "return no content when PAYE known facts do not match" in {
      givenEs20CallReturnsNoContent(
        expectedBody = Json.obj(
          "service" -> PAYE.enrolmentKey,
          "knownFacts" -> Json.arr(
            Json.obj("key" -> "IRAgentReference", "value" -> "A12345"),
            Json.obj("key" -> "IRAgentPostcode", "value" -> "AA1 1AA")
          )
        ).toString
      )

      connector.queryKnownFactsForAgent(PAYE, "A12345", PayePostcode.from(Some("AA1 1AA"))).futureValue shouldBe None
    }

    "return none without calling ES20 when PAYE postcode is blank" in {
      connector.queryKnownFactsForAgent(PAYE, "A12345", PayePostcode.from(Some("   "))).futureValue shouldBe None
      verifyWiremock(0, postRequestedFor(urlEqualTo("/enrolment-store-proxy/enrolment-store/enrolments")))
    }
  }

  "ES9" should {
    "deallocate enrolment successfully" in {
      givenEs9CallSucceeds(testGroupId, SA, "A12345")

      connector.deallocateAgentEnrolment(testGroupId, SA, "A12345").futureValue
    }

    "throw error when tax-enrolments returns an error" in {
      givenEs9CallFails(testGroupId, SA, "A12345")

      intercept[TestFailedException](connector.deallocateAgentEnrolment(testGroupId, SA, "A12345").futureValue)
    }
  }
}
