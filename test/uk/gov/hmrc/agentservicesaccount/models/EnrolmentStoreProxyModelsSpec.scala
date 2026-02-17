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

package uk.gov.hmrc.agentservicesaccount.models

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class EnrolmentStoreProxyModelsSpec extends UnitSpec:

  "EspKnownFact" should {
    "round-trip to JSON" in {
      val model = EspKnownFact("IRAgentReference", "A12345")
      val json = Json.toJson(model)

      json.as[EspKnownFact] shouldBe model
    }
  }

  "Es20Request" should {
    "round-trip to JSON" in {
      val model = Es20Request(
        service = "IR-PAYE-AGENT",
        knownFacts = Seq(EspKnownFact("IRAgentReference", "A12345"))
      )
      val json = Json.toJson(model)

      json.as[Es20Request] shouldBe model
    }
  }

  "Es20Enrolment" should {
    "round-trip to JSON" in {
      val model = Es20Enrolment(
        identifiers = Seq(EspKnownFact("IRAgentReference", "A12345")),
        verifiers = Seq(EspKnownFact("IRAgentPostcode", "AA1 1AA"))
      )
      val json = Json.toJson(model)

      json.as[Es20Enrolment] shouldBe model
    }
  }

  "Es20Response" should {
    "round-trip to JSON" in {
      val model = Es20Response(
        service = "IR-PAYE-AGENT",
        enrolments = Seq(
          Es20Enrolment(
            identifiers = Seq(EspKnownFact("IRAgentReference", "A12345")),
            verifiers = Seq(EspKnownFact("IRAgentPostcode", "AA1 1AA"))
          )
        )
      )
      val json = Json.toJson(model)

      json.as[Es20Response] shouldBe model
    }
  }

  "Es8Request" should {
    "round-trip to JSON with the expected type field" in {
      val model = Es8Request("admin-cred", "principal", "enrolAndActivate")
      val json: JsValue = Json.toJson(model)

      (json \ "type").as[String] shouldBe "principal"
      (json \ "action").as[String] shouldBe "enrolAndActivate"
      json.as[Es8Request] shouldBe model
    }
  }
