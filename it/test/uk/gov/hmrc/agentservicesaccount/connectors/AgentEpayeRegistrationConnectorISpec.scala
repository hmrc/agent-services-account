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

import org.scalatest.exceptions.TestFailedException
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.stubs.AgentEpayeRegistrationStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

import scala.concurrent.ExecutionContext

class AgentEpayeRegistrationConnectorISpec
extends ComponentSpecHelper
with AgentEpayeRegistrationStubs {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  lazy val connector: AgentEpayeRegistrationConnector = app.injector.instanceOf[AgentEpayeRegistrationConnector]

  val testSubscriptionRequest = PayeSubscriptionRequest(
    agentName = "Test Agency",
    contactName = "John Agent",
    phoneNumber = Some("1234567890"),
    emailAddress = Some("test@email.com"),
    address = SubscriptionAddress(
      line1 = "Line 1",
      line2 = "Line 2",
      line3 = Some("Line 3"),
      line4 = Some("Line 4"),
      postCode = Some("A11 11A")
    )
  )
  val testAgentReference = AgentReference("AB1234")

  "register" should {
    "return agent reference on a successful response" in {
      givenEpayeRegisterCallSucceeds(testSubscriptionRequest)(testAgentReference)

      val result = connector.register(testSubscriptionRequest).futureValue

      result shouldBe testAgentReference
    }

    "return agent reference when payeAgentReference is returned" in {
      givenEpayeRegisterCallSucceedsWithPayeReference(testSubscriptionRequest)(testAgentReference)

      val result = connector.register(testSubscriptionRequest).futureValue

      result shouldBe testAgentReference
    }

    "throw error when OPRA returns unexpected response" in {
      givenEpayeRegisterCallFails(testSubscriptionRequest)

      intercept[TestFailedException](connector.register(testSubscriptionRequest).futureValue)
    }
  }

}
