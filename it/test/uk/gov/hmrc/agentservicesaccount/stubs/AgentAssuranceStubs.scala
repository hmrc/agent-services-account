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

package uk.gov.hmrc.agentservicesaccount.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.Eventually.*
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

trait AgentAssuranceStubs {

  def givenAgentUtrCheckWithRefusalToDealWithTrue(utr: Utr): Unit =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/managed-utrs/utr/${utr.value}?nameRequired=true"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"isManuallyAssured":false,"isRefusalToDealWith":true}""")
        )
    )

  def givenAgentUtrCheckWithRefusalToDealWithFalse(utr: Utr): Unit =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/managed-utrs/utr/${utr.value}?nameRequired=true"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"isManuallyAssured":false,"isRefusalToDealWith":false}""")
        )
    )
  
  
  def givenAgentUtrCheckReturnsError(utr: Utr, status: Int): Unit =
    stubFor(
      get(urlEqualTo(s"/agent-assurance/managed-utrs/utr/${utr.value}?nameRequired=true"))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def verifyAgentUtrCheckWasCalled(utr: Utr, count: Int = 1): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        getRequestedFor(
          urlEqualTo(s"/agent-assurance/managed-utrs/utr/${utr.value}?nameRequired=true")
        )
      )
    }
}
