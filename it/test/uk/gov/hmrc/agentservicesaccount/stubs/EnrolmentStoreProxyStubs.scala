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
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.Es20Response
import uk.gov.hmrc.agentservicesaccount.models.GroupId

trait EnrolmentStoreProxyStubs:

  def givenEs3CallSucceeds(groupId: GroupId)(regimes: LegacyRegime*): Unit = stubFor(
    get(urlEqualTo(s"/enrolment-store-proxy/enrolment-store/groups/${groupId.value}/enrolments?type=principal"))
      .willReturn(
        aResponse()
          .withStatus(if regimes.nonEmpty then 200 else 204)
          .withBody(Json.obj(
            "enrolments" -> Json.arr(regimes.map { regime =>
              Json.obj(
                "service" -> regime.enrolmentKey,
                "state" -> "Activated"
              )
            }*)
          ).toString)
      )
  )

  def givenEs3CallFails(groupId: GroupId): Unit = stubFor(
    get(urlEqualTo(s"/enrolment-store-proxy/enrolment-store/groups/${groupId.value}/enrolments?type=principal"))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
  )

  def givenEs20CallSucceeds(
    expectedBody: String,
    response: Es20Response
  ): Unit = stubFor(
    post(urlEqualTo("/enrolment-store-proxy/enrolment-store/enrolments"))
      .withRequestBody(equalToJson(
        expectedBody,
        true,
        true
      ))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(Json.toJson(response).toString)
      )
  )

  def givenEs20CallReturnsNoContent(expectedBody: String): Unit = stubFor(
    post(urlEqualTo("/enrolment-store-proxy/enrolment-store/enrolments"))
      .withRequestBody(equalToJson(
        expectedBody,
        true,
        true
      ))
      .willReturn(
        aResponse()
          .withStatus(204)
      )
  )

  def givenEs9CallSucceeds(
    groupId: GroupId,
    regime: LegacyRegime,
    agentReference: String
  ): Unit = {
    val enrolmentKey = s"${regime.enrolmentKey}~${regime.agentReferenceKey}~$agentReference"
    stubFor(
      delete(urlEqualTo(s"/tax-enrolments/groups/${groupId.value}/enrolments/$enrolmentKey"))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )
  }

  def givenEs9CallFails(
    groupId: GroupId,
    regime: LegacyRegime,
    agentReference: String
  ): Unit = {
    val enrolmentKey = s"${regime.enrolmentKey}~${regime.agentReferenceKey}~$agentReference"
    stubFor(
      delete(urlEqualTo(s"/tax-enrolments/groups/${groupId.value}/enrolments/$enrolmentKey"))
        .willReturn(
          aResponse()
            .withStatus(500)
        )
    )
  }
