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

  private def responseJson(regime: Option[(String, JsValue)]): JsObject = Json.obj("success" -> JsObject(baseSuccessJson.fields ++ regime.toSeq))

  private def readRegime(regime: Option[(String, JsValue)]): Option[Seq[String]] =
    Json.fromJson[HipAgentSubscriptionResponse](responseJson(regime)).get.success.regime

  "HipAgentSubscriptionResponse" should:
    "read regime as a single-item sequence when HIP returns a string" in:
      readRegime(Some("regime" -> JsString("ALL"))) shouldBe Some(Seq("ALL"))

    "read regime when HIP returns an array of strings" in:
      readRegime(Some("regime" -> JsArray(Seq(JsString("ITSA"), JsString("VAT"))))) shouldBe Some(Seq("ITSA", "VAT"))

    "filter blank regime values" in:
      readRegime(Some("regime" -> JsArray(Seq(
        JsString("ITSA"),
        JsString(""),
        JsString(" ")
      )))) shouldBe Some(Seq("ITSA"))

    "read missing regime as None" in:
      readRegime(None) shouldBe None

    "read null regime as None" in:
      readRegime(Some("regime" -> JsNull)) shouldBe None

    "fail when regime has an unsupported JSON type" in:
      Json.fromJson[HipAgentSubscriptionResponse](responseJson(Some("regime" -> JsNumber(1)))).isError shouldBe true

    "filter out defaulted address lines" in:
      val response = Json.obj(
        "success" -> Json.obj(
          "processingDate" -> "2025-02-25",
          "utr" -> "123456",
          "name" -> "ABC Accountants",
          "addr1" -> "Matheson House",
          "addr2" -> "Address Line 2",
          "addr3" -> "Address Line 3",
          "addr4" -> "Address Line 4",
          "postcode" -> "Postcode",
          "country" -> "GB",
          "phone" -> "07345678901",
          "email" -> "abc@xyz.com",
          "suspensionStatus" -> "T",
          "supervisoryBody" -> "HMRC",
          "membershipNumber" -> "AMLS123",
          "evidenceObjectReference" -> "evidence-ref-001"
        )
      ).as[HipAgentSubscriptionResponse]

      response.success.addr2 shouldBe None
      response.success.addr3 shouldBe None
      response.success.addr4 shouldBe None
      response.success.postcode shouldBe None

    "read normal address lines" in:
      val response = Json.obj(
        "success" -> baseSuccessJson
      ).as[HipAgentSubscriptionResponse]

      response.success.addr2 shouldBe Some("Grange Central")
      response.success.addr3 shouldBe Some("Town Centre")
      response.success.addr4 shouldBe Some("Telford")
      response.success.postcode shouldBe Some("TF3 4ER")

    "parse UpdateStatus successfully" in:
      val statusFields = Json.obj(
        "updateDetailsStatus" -> "ACCEPTED",
        "amlSupervisionUpdateStatus" -> "PENDING",
        "directorPartnerUpdateStatus" -> "REQUIRED",
        "acceptNewTermsStatus" -> "REJECTED"
      )

      val response = Json.obj(
        "success" -> (baseSuccessJson ++ statusFields)
      ).as[HipAgentSubscriptionResponse]

      response.success.updateDetailsStatus shouldBe Some(UpdateStatus.ACCEPTED)
      response.success.amlSupervisionUpdateStatus shouldBe Some(UpdateStatus.PENDING)
      response.success.directorPartnerUpdateStatus shouldBe Some(UpdateStatus.REQUIRED)
      response.success.acceptNewTermsStatus shouldBe Some(UpdateStatus.REJECTED)
      response.success.reriskStatus shouldBe None

    "parse invalid UpdateStatus values as None" in:
      val statusFields = Json.obj(
        "updateDetailsStatus" -> " ",
        "amlSupervisionUpdateStatus" -> "",
        "directorPartnerUpdateStatus" -> "NOT_VALID",
        "acceptNewTermsStatus" -> "null",
        "reriskStatus" -> "ACCEPTED    "
      )

      val response = Json.obj(
        "success" -> (baseSuccessJson ++ statusFields)
      ).as[HipAgentSubscriptionResponse]

      response.success.updateDetailsStatus shouldBe None
      response.success.amlSupervisionUpdateStatus shouldBe None
      response.success.directorPartnerUpdateStatus shouldBe None
      response.success.acceptNewTermsStatus shouldBe None
      response.success.reriskStatus shouldBe Some(UpdateStatus.ACCEPTED)
