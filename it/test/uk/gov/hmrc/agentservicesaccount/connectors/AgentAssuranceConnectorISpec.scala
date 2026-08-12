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


import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.UtrChecksResponse
import uk.gov.hmrc.agentservicesaccount.stubs.AgentAssuranceStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext

class AgentAssuranceConnectorISpec
  extends ComponentSpecHelper
    with AgentAssuranceStubs {
  
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  override def extraConfig: Map[String, Any] = Map(
    "microservice.services.agent-assurance.host" -> mockHost,
    "microservice.services.agent-assurance.port" -> mockPort,
    "auditing.enabled" -> false,
    "http-verbs.retries.intervals" -> List("1ms")
  )


  lazy val connector = new AgentAssuranceConnector(
    app.injector.instanceOf[AppConfig],
    app.injector.instanceOf[HttpClientV2]
  )(using ec)

  val utr = Utr("1234567890")

  "AgentAssuranceConnector.getAgentUtrChecks" should {
    "return successful UtrChecksResponse when agent passes checks" in {
      givenAgentUtrCheckWithRefusalToDealWithTrue(utr)

      val result = connector.getAgentUtrChecks(utr).futureValue

      result shouldBe UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = true,
        businessName = None
      )
    }

    "return UtrChecksResponse with false values when agent fails checks" in {
      givenAgentUtrCheckWithRefusalToDealWithFalse(utr)

      val result = connector.getAgentUtrChecks(utr).futureValue

      result shouldBe UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = false,
        businessName = None
      )
    }

    "throw exception when agent assurance service returns error" in {
      givenAgentUtrCheckReturnsError(utr, 500)

      val thrown = intercept[Exception] {
        await(connector.getAgentUtrChecks(utr))
      }

      thrown.getMessage should include("500")
    }
  }
}


