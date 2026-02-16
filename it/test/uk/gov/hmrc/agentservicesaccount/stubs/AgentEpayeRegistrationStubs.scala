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
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayeSubscriptionRequest

trait AgentEpayeRegistrationStubs:

  def givenEpayeRegisterCallSucceeds(request: PayeSubscriptionRequest)(agentReference: AgentReference): Unit = stubFor(
    post(urlEqualTo("/agent-epaye-registration/registrations"))
      .withRequestBody(equalToJson(
        Json.toJson(request)(PayeSubscriptionRequest.registerWrites).toString
      ))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(Json.obj(
            "agentReference" -> agentReference
          ).toString)
      )
  )

  def givenEpayeRegisterCallFails(request: PayeSubscriptionRequest): Unit = stubFor(
    post(urlEqualTo(s"/agent-epaye-registration/registrations"))
      .withRequestBody(equalToJson(
        Json.toJson(request)(PayeSubscriptionRequest.registerWrites).toString
      ))
      .willReturn(
        aResponse()
          .withStatus(400)
      )
  )

  def verifyPayeRegisterCall(
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        postRequestedFor(
          urlEqualTo("/agent-epaye-registration/registrations")
        )
      )
    }
