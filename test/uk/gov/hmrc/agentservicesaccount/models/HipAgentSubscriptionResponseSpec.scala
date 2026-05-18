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

import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class HipAgentSubscriptionResponseSpec
extends UnitSpec:

  private val baseSuccessJson = Json.obj(
    "processingDate" -> "2025-02-25",
    "utr" -> "123456",
    "name" -> "ABC Accountants",
    "addr1" -> "Matheson House",
    "addr2" -> "Grange Central",
    "addr3" -> "Town Centre",
    "addr4" -> "Telford",
    "postcode" -> "TF3 4ER",
    "country" -> "GB",
    "phone" -> "07345678901",
    "email" -> "abc@xyz.com",
    "suspensionStatus" -> "T",
    "supervisoryBody" -> "HMRC",
    "membershipNumber" -> "AMLS123",
    "evidenceObjectReference" -> "evidence-ref-001"
  )

  private def responseJson(regime: Option[(String, JsValue)]): JsObject =
    Json.obj("success" -> JsObject(baseSuccessJson.fields ++ regime.toSeq))

  private def readRegime(regime: Option[(String, JsValue)]): Option[Seq[String]] =
    Json.fromJson[HipAgentSubscriptionResponse](responseJson(regime)).get.success.regime

  "HipAgentSubscriptionResponse" should:
    "read regime as a single-item sequence when HIP returns a string" in:
      readRegime(Some("regime" -> JsString("ALL"))) shouldBe Some(Seq("ALL"))

    "read regime when HIP returns an array of strings" in:
      readRegime(Some("regime" -> JsArray(Seq(JsString("ITSA"), JsString("VAT"))))) shouldBe Some(Seq("ITSA", "VAT"))

    "filter blank regime values" in:
      readRegime(Some("regime" -> JsArray(Seq(JsString("ITSA"), JsString(""), JsString(" "))))) shouldBe Some(Seq("ITSA"))

    "read missing regime as None" in:
      readRegime(None) shouldBe None

    "read null regime as None" in:
      readRegime(Some("regime" -> JsNull)) shouldBe None

    "fail when regime has an unsupported JSON type" in:
      Json.fromJson[HipAgentSubscriptionResponse](responseJson(Some("regime" -> JsNumber(1)))).isError shouldBe true
