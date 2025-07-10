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
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR}
import play.api.libs.json.Json

trait DmsSubmissionStubs {

  def givenDmsSubmissionSuccess: StubMapping = stubFor(
    post(urlEqualTo("/dms-submission/submit"))
      .willReturn(
        aResponse()
          .withStatus(ACCEPTED)
      )
  )

  def givenDmsFullSubmissionSuccess: StubMapping = stubFor(
      post(urlEqualTo("/dms-submission/submit"))
        .withHeader("Authorization", equalTo("authKey"))
        .withHeader("User-Agent", equalTo("agent-services-account"))
        .withMultipartRequestBody(aMultipart().withName("submissionReference").withBody(equalTo("submissionReference")))
        .withMultipartRequestBody(aMultipart().withName("callbackUrl").withBody(equalTo("http://localhost/callback")))
        .withMultipartRequestBody(aMultipart().withName("metadata.source").withBody(equalTo("source")))
        .withMultipartRequestBody(aMultipart().withName("metadata.timeOfReceipt").withBody(equalTo("2022-03-02T12:30:45Z")))
        .withMultipartRequestBody(aMultipart().withName("metadata.formId").withBody(equalTo("formId")))
        .withMultipartRequestBody(aMultipart().withName("metadata.customerId").withBody(equalTo("customerId")))
        .withMultipartRequestBody(aMultipart().withName("metadata.classificationType").withBody(equalTo("classificationType")))
        .withMultipartRequestBody(aMultipart().withName("metadata.businessArea").withBody(equalTo("businessArea")))
        .withMultipartRequestBody(
          aMultipart()
            .withName("form")
            .withBody(equalTo("SomePdfBytes"))
            .withHeader("Content-Disposition", containing("""filename="form.pdf""""))
            .withHeader("Content-Type", equalTo("application/pdf"))
        )
        .willReturn(
          aResponse()
            .withStatus(ACCEPTED)
            .withBody(Json.stringify(Json.obj("id" -> "foobar")))
        )
    )

  def givenDmsSubmission5xx: StubMapping = stubFor(
    post(urlEqualTo("/dms-submission/submit"))
      .willReturn(
        aResponse()
          .withStatus(INTERNAL_SERVER_ERROR)
      )
  )

}
