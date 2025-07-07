/*
 * Copyright 2024 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import play.api.http.Status.{CREATED, NOT_FOUND, OK}
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.AUTHORIZATION
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

trait InternalAuthStub {

  def stubInternalAuthorisedDynamicArn(arn: Arn): StubMapping = {
    val resourceLocation = s"agent-record-with-checks/arn/${arn.value}"

    stubFor(
      post(urlEqualTo("/internal-auth/auth"))
        .withRequestBody(equalToJson(
          s"""
             |{
             |  "predicate": {
             |    "action": "WRITE",
             |    "resource": {
             |      "resourceType": "agent-services-account",
             |      "resourceLocation": "$resourceLocation"
             |    }
             |  },
             |  "retrieve": []
             |}
             |""".stripMargin,
          true, // ignoreArrayOrder
          true // ignoreExtraElements
        ))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"retrievals": []}""")
        )
    )
  }


  def stubInternalAuthorisedLenient(): StubMapping = stubFor(
    post(urlEqualTo("/internal-auth/auth"))
      .withRequestBody(matchingJsonPath("$.predicate.resource.resourceType", equalTo("agent-services-account")))
      .withRequestBody(matchingJsonPath("$.predicate.resource.resourceLocation", containing("agent-record-with-checks/arn")))
      .withRequestBody(matchingJsonPath("$.predicate.action", equalTo("WRITE")))
      .willReturn(
        aResponse()
          .withStatus(OK)
          .withBody("""{"retrievals": []}""")
      )
  )

  def stubInternalAuthorisedWithPermission(
                                            resourceType: String = "agent-services-account",
                                            resourceLocation: String = "agent-record-with-checks/arn",
                                            action: String = "WRITE"
                                          ): StubMapping = {
    val expectedPredicate =
      s"""
         |{
         |  "predicate": {
         |    "action": "$action",
         |    "resource": {
         |      "resourceType": "$resourceType",
         |      "resourceLocation": "$resourceLocation"
         |    }
         |  }
         |}
         |""".stripMargin

    stubFor(
      post(urlEqualTo("/internal-auth/auth"))
        .withRequestBody(equalToJson(expectedPredicate, true, true))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody("""{"retrievals": {}}""")
        )
    )
  }

  def stubInternalAuthorised(): StubMapping = stubFor(
    post(urlEqualTo("/internal-auth/auth"))
      .willReturn(
        aResponse()
          .withStatus(OK)
          .withBody("""{"retrievals": {}}""".stripMargin)
      )
  )

  def getTestStubInternalAuthorised(): StubMapping = stubFor(
    get(urlMatching("/test-only/token"))
      .willReturn(aResponse().withStatus(NOT_FOUND))
  )

  def postTestStubInternalAuthorised(): StubMapping = stubFor(
    post(urlMatching("/test-only/token"))
      .willReturn(aResponse().withStatus(CREATED))
  )

  def verifyGetTestStubInternalAuth(
    authToken: String,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(30, Seconds))) {
      verify(
        count,
        getRequestedFor(urlMatching("/test-only/token"))
          .withHeader(AUTHORIZATION, equalTo(authToken))
      )
    }

  def verifyPostTestStubInternalAuth(
    expectedRequest: JsObject,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(30, Seconds))) {
      verify(
        count,
        postRequestedFor(urlMatching("/test-only/token"))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(expectedRequest))))
      )
    }

}
