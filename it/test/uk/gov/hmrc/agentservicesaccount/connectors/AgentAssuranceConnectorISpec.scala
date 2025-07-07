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


import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import play.api.{Application, Configuration}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.UtrChecksResponse
import uk.gov.hmrc.agentservicesaccount.stubs.{AgentAssuranceStubs, MetricTestSupport}
import uk.gov.hmrc.agentservicesaccount.support.{UnitSpec, WireMockSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext

class AgentAssuranceConnectorISpec
  extends UnitSpec
    with GuiceOneAppPerSuite
    with WireMockSupport
    with AgentAssuranceStubs
    with MetricTestSupport {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private implicit val ec: ExecutionContext = ExecutionContext.global

  lazy val connector = new AgentAssuranceConnector(
    app.injector.instanceOf[HttpClientV2],
    app.injector.instanceOf[Metrics]
  )(ec, app.injector.instanceOf[AppConfig])

  override implicit lazy val app: Application = appBuilder.build()

  private def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.agent-assurance.host" -> wireMockHost,
        "microservice.services.agent-assurance.port" -> wireMockPort,
        "auditing.enabled" -> false,
        "http-verbs.retries.intervals" -> List("1ms")
      )

  val utr = Utr("1234567890")

  "AgentAssuranceConnector.getAgentUtrChecks" should {
    "return successful UtrChecksResponse when agent passes checks" in {
      givenAgentUtrCheckWithRefusalToDealWithTrue(utr)

      val result = await(connector.getAgentUtrChecks(utr))

      result shouldBe UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = true,
        businessName = None
      )
    }

    "return UtrChecksResponse with false values when agent fails checks" in {
      givenAgentUtrCheckWithRefusalToDealWithFalse(utr)

      val result = await(connector.getAgentUtrChecks(utr))

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


