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
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}

trait HipStubs {

  def givenHIPGetAgentRecordSuspendedAgent(
    arn: Arn,
    utr: String = "123456"
  ) = stubFor(
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
                  "suspensionStatus": "T",
                  "supervisoryBody": "HMRC",
                  "membershipNumber": "AMLS123",
                  "evidenceObjectReference": "evidence-ref-001"
                }
              }
            """
        )
      )
  )

  def givenHIPGetAgentRecordSuspendedAgentWithStringRegime(
    arn: Arn,
    utr: String = "123456",
    regime: String = "ALL"
  ) = stubFor(
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
                  "suspensionStatus": "T",
                  "regime": "$regime",
                  "supervisoryBody": "HMRC",
                  "membershipNumber": "AMLS123",
                  "evidenceObjectReference": "evidence-ref-001"
                }
              }
            """
        )
      )
  )

  private def personalDetailsResponseBodyWithValidData(
                                                optUtr: Option[Utr],
                                                overseas: Boolean
                                              ) =
    s"""
       |{
       |   "isAnOrganisation" : true,
       |   "contactDetails" : {
       |      "phoneNumber" : "07000000000"
       |   },
       |   "isAnAgent" : true,
       |   "safeId" : "XB0000100101711",
       |   """.stripMargin ++ optUtr
      .map(utr =>
        s""" "uniqueTaxReference": "${utr.value}",
           |""".stripMargin)
      .getOrElse("") ++
      s""" "agencyDetails" : {
         |      "agencyAddress" : {
         |         "addressLine2" : "Grange Central",
         |         "addressLine3" : "Town Centre",
         |         "addressLine4" : "Telford",
         |         "postalCode" : "TF3 4ER",
         |         "countryCode" : "${
        if (overseas)
          "NZ"
        else
          "GB"
      }",
         |         "addressLine1" : "Matheson House"
         |      },
         |      "agencyName" : "ABC Accountants",
         |      "agencyEmail" : "abc@xyz.com",
         |      "agencyTelephone" : "07345678901"
         |   },
         |   "suspensionDetails": {"suspensionStatus": false},
         |   "organisation" : {
         |      "organisationName" : "CT AGENT 183",
         |      "isAGroup" : false,
         |      "organisationType" : "0000"
         |   },
         |   "addressDetails" : {
         |      "addressLine2" : "Grange Central 183",
         |      "addressLine3" : "Telford 183",
         |      "addressLine4" : "Shropshire 183",
         |      "postalCode" : "TF3 4ER",
         |      "countryCode" : "GB",
         |      "addressLine1" : "Matheson House 183"
         |   },
         |   "individual" : {
         |      "firstName" : "John",
         |      "lastName" : "Smith"
         |   },
         |   "isAnASAgent" : true,
         |   "isAnIndividual" : false,
         |   "businessPartnerExists" : true,
         |   "agentReferenceNumber" : "TestARN"
         |}
            """.stripMargin

//  TODO: 11995 Need to sort this
  def givenHipGetAgentRecord(
                              arn: Arn,
                              utr: Option[Utr],
                              overseas: Boolean = false
                            ) = stubFor(
    get(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(personalDetailsResponseBodyWithValidData(utr, overseas))
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

  def givenHipAmendAgentRecordSuccess(arn: Arn) = stubFor(
    put(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        okJson("""{"success":{"processingDate":"2024-07-15T09:30:47Z"}}""")
      )
  )

  def givenHipAmendAgentRecordError(
    arn: Arn,
    status: Int
  ) = stubFor(
    put(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        aResponse().withStatus(status)
      )
  )

  def verifyHipAmendAgentRecord(
    arn: Arn,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        putRequestedFor(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      )
    }

}
