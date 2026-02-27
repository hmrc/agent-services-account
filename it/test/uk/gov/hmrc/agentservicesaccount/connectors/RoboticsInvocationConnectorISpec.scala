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

package uk.gov.hmrc.agentservicesaccount.connectors

import com.github.tomakehurst.wiremock.client.WireMock.{post as wmPost, stubFor, urlEqualTo, aResponse}
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext

class RoboticsInvocationConnectorISpec extends ComponentSpecHelper {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private given HeaderCarrier = HeaderCarrier()

  lazy implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  lazy val connector: RoboticsInvocationConnector =
    new RoboticsInvocationConnector(
      appConfig,
      httpClient
    )

  private val invocationPath = "/RTServer/rest/nice/rti/ra/invocation"

  "invoke" should {

    "treat non-200 2xx responses as success (guards against HIP/proxy variations)" in {
      val payload = Json.obj("postcode" -> "A11 11A")
      val correlationId = CorrelationId("corr-id-202")

      List(200, 201, 202, 204).foreach { status =>
        stubFor(
          wmPost(urlEqualTo(invocationPath))
            .willReturn(aResponse().withStatus(status))
        )

        val result = connector.invoke(payload, correlationId).futureValue

        result shouldBe (())
        resetWiremock()
      }
    }

    "throw UpstreamErrorResponse for non-2xx responses (and not include payload in message)" in {
      stubFor(
        wmPost(urlEqualTo(invocationPath))
          .willReturn(aResponse().withStatus(500).withBody("""{"error":"boom"}"""))
      )

      val payload = Json.obj("postcode" -> "A11 11A")
      val correlationId = CorrelationId("corr-id-500")

      val ex = connector.invoke(payload, correlationId).failed.futureValue
      ex shouldBe a[UpstreamErrorResponse]
      val upstreamEx = ex.asInstanceOf[UpstreamErrorResponse]

      upstreamEx.message should include("status=500")
      upstreamEx.message should include("correlationId=corr-id-500")
      upstreamEx.message should not include "A11 11A"
    }
  }

}
