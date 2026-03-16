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
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

trait HipStubs {

  def givenHIPGetAgentRecordSuspendedAgent(arn: Arn, utr:String = "123456") = stubFor(
    get(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        okJson(
          s"""
              {
                "success": {
                  "processingDate": "2025-02-25",
                  "utr": $utr,
                  "name": "ABC Accountants",
                  "addr1": "Matheson House",
                  "addr2": "Grange Central",
                  "addr3": "Town Centre",
                  "addr4": "Telford",
                  "postcode": "TF3 4ER",
                  "country": "GB",
                  "phone": "07345678901",
                  "email": "abc@xyz.com",
                  "suspensionStatus": "T"
                }
              }
            """
        )
      )
  )

  def verifyHipGetAgentRecord(
    arn: Arn,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        getRequestedFor(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      )
    }

  def givenHipAgentIsUnknown404(arn: Arn) = {
    stubFor(
      get(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )
  }

  def givenHipReturnsServerError(arn: Arn) = {
    stubFor(
      get(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
        .willReturn(serverError())
    )
  }

}
