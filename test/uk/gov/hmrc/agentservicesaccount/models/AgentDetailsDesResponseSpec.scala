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

package uk.gov.hmrc.agentservicesaccount.models

import org.scalatest.matchers.must.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.*

class AgentDetailsDesResponseSpec
extends UnitSpec:

  // Fake crypto for encryption tests
  given fakeCrypto: Encrypter
    with Decrypter
    with

    override def encrypt(plain: PlainContent): Crypted =
      plain match
        case PlainText(value) => Crypted(s"ENC($value)")
        case PlainBytes(value) => Crypted(s"ENC(${String(value)})")

    override def decrypt(crypted: Crypted): PlainText =
      val raw = crypted.value.stripPrefix("ENC(").stripSuffix(")")
      PlainText(raw)

    override def decryptAsBytes(crypted: Crypted): PlainBytes =
      val raw = crypted.value.stripPrefix("ENC(").stripSuffix(")")
      PlainBytes(raw.getBytes("UTF-8"))

  val testUtr = Utr("1234567890")
  val testSuspension = SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))

  val testAgencyDetails = AgencyDetails(
    Some("Test Agency"),
    Some("email@test.com"),
    Some("123456789"),
    Some(BusinessAddress(
      "Line 1",
      None,
      None,
      None,
      Some("AB1 2CD"),
      "GB"
    ))
  )

  val testAgentDetails = AgentDetailsDesResponse(
    uniqueTaxReference = Some(testUtr),
    agencyDetails = Some(testAgencyDetails),
    suspensionDetails = Some(testSuspension),
    isAnIndividual = Some(false)
  )

  "AgentDetailsDesResponse" should {

    "serialize to JSON using the standard format" in {
      val json = Json.toJson(testAgentDetails)
      (json \ "uniqueTaxReference").as[String] mustBe "1234567890"
      (json \ "agencyDetails" \ "agencyName").as[String] mustBe "Test Agency"
    }

    "deserialize from JSON using the standard format" in {
      val json = Json.obj(
        "uniqueTaxReference" -> "1234567890",
        "agencyDetails" -> Json.obj(
          "agencyName" -> "Test Agency",
          "agencyEmail" -> "email@test.com",
          "agencyTelephone" -> "123456789",
          "agencyAddress" -> Json.obj(
            "addressLine1" -> "Line 1",
            "postalCode" -> "AB1 2CD",
            "countryCode" -> "GB"
          )
        ),
        "suspensionDetails" -> Json.obj(
          "suspensionStatus" -> true,
          "regimes" -> Json.arr("ITSA")
        ),
        "isAnIndividual" -> false
      )

      val result = Json.fromJson[AgentDetailsDesResponse](json).get
      result mustBe testAgentDetails
    }

    "serialize to encrypted JSON" in {
      val encrypted = Json.toJson(testAgentDetails)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat)
      (encrypted \ "uniqueTaxReference").as[String] must startWith("ENC(")
    }

    "deserialize from encrypted JSON" in {
      val encrypted = Json.toJson(testAgentDetails)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat)
      val roundtrip = Json.fromJson[AgentDetailsDesResponse](encrypted)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat).get
      roundtrip mustBe testAgentDetails
    }

    "support partial objects" in {
      val partial = AgentDetailsDesResponse(
        None,
        None,
        None,
        None
      )
      val json = Json.toJson(partial)
      val result = Json.fromJson[AgentDetailsDesResponse](json).get
      result mustBe partial
    }
  }
