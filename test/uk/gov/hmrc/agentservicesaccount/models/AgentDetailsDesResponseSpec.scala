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
import uk.gov.hmrc.agentservicesaccount.models.AmlsDetails.*
import uk.gov.hmrc.agentservicesaccount.models.UpdateStatus.ACCEPTED
import uk.gov.hmrc.agentservicesaccount.models.UpdateStatus.REJECTED
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

  val testAmlsDetails = AmlsDetails(
    SupervisoryBody("HMRC"),
    MembershipNumber("AMLS123"),
    Some(EvidenceObjectReference("evidence-ref-001"))
  )

  val testAgentDetails = AgentDetailsDesResponse(
    uniqueTaxReference = Some(testUtr),
    agencyDetails = Some(testAgencyDetails),
    suspensionDetails = Some(testSuspension),
    isAnIndividual = Some(false),
    amlsDetails = Some(testAmlsDetails),
    updateDetailsStatus = Some(ACCEPTED),
    amlSupervisionUpdateStatus = Some(REJECTED)
  )

  "AgentDetailsDesResponse" should {

    "serialize to JSON using the standard format" in {
      val json = Json.toJson(testAgentDetails)
      (json \ "uniqueTaxReference").as[String] mustBe "1234567890"
      (json \ "agencyDetails" \ "agencyName").as[String] mustBe "Test Agency"
      (json \ "amlsDetails" \ "supervisoryBody").as[String] mustBe "HMRC"
      (json \ "amlsDetails" \ "membershipNumber").as[String] mustBe "AMLS123"
      (json \ "amlsDetails" \ "evidenceObjectReference").as[String] mustBe "evidence-ref-001"
      (json \ "updateDetailsStatus").as[String] mustBe "ACCEPTED"
      (json \ "amlSupervisionUpdateStatus").as[String] mustBe "REJECTED"
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
        "isAnIndividual" -> false,
        "amlsDetails" -> Json.obj(
          "supervisoryBody" -> "HMRC",
          "membershipNumber" -> "AMLS123",
          "evidenceObjectReference" -> "evidence-ref-001"
        ),
        "updateDetailsStatus" -> "ACCEPTED",
        "amlSupervisionUpdateStatus" -> "REJECTED"
      )

      val result = Json.fromJson[AgentDetailsDesResponse](json).get
      result mustBe testAgentDetails
    }

    "serialize to encrypted JSON" in {
      val encrypted = Json.toJson(testAgentDetails)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat)
      (encrypted \ "uniqueTaxReference").as[String] must startWith("ENC(")
      (encrypted \ "amlsDetails" \ "supervisoryBody").as[String] must startWith("ENC(")
      (encrypted \ "amlsDetails" \ "membershipNumber").as[String] must startWith("ENC(")
      (encrypted \ "amlsDetails" \ "evidenceObjectReference").as[String] must startWith("ENC(")
      (encrypted \ "updateDetailsStatus").as[String] mustBe "ACCEPTED"
      (encrypted \ "amlSupervisionUpdateStatus").as[String] mustBe "REJECTED"
      (encrypted \ "directorPartnerUpdateStatus").isDefined mustBe false
      (encrypted \ "acceptNewTermsStatus").isDefined mustBe false
      (encrypted \ "reriskStatus").isDefined mustBe false
    }

    "deserialize from encrypted JSON" in {
      val encrypted = Json.toJson(testAgentDetails)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat)
      val roundtrip = Json.fromJson[AgentDetailsDesResponse](encrypted)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat).get
      roundtrip mustBe testAgentDetails
    }

    "deserialize from database JSON where UpdateStatus value is not valid" in {
      val encrypted = Json.obj(
        "updateDetailsStatus" -> " ",
        "amlSupervisionUpdateStatus" -> "",
        "directorPartnerUpdateStatus" -> JsNull,
        "acceptNewTermsStatus" -> "INVALID",
        "reriskStatus" -> "ACCEPTED   "
      )

      val result = Json.fromJson[AgentDetailsDesResponse](encrypted)(using AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat).get

      result.updateDetailsStatus mustBe None
      result.amlSupervisionUpdateStatus mustBe None
      result.directorPartnerUpdateStatus mustBe None
      result.acceptNewTermsStatus mustBe None
      result.reriskStatus mustBe Some(UpdateStatus.ACCEPTED)
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

    "create HipAmendPayload UpdateStatus defaults to ACCEPTED" in {
      val agencyDetailsUpdateStatus = AgentDetailsDesResponse(
        uniqueTaxReference = None,
        agencyDetails = None,
        suspensionDetails = None,
        isAnIndividual = None,
        updateDetailsStatus = Some(UpdateStatus.ACCEPTED),
        amlSupervisionUpdateStatus = Some(UpdateStatus.REJECTED),
        directorPartnerUpdateStatus = None,
        acceptNewTermsStatus = None,
        reriskStatus = None
      )
      val expected = HipAmendPayload(
        updateDetailsStatus = Some(UpdateStatus.ACCEPTED),
        amlSupervisionUpdateStatus = Some(UpdateStatus.REJECTED),
        directorPartnerUpdateStatus = Some(UpdateStatus.ACCEPTED),
        acceptNewTermsStatus = Some(UpdateStatus.ACCEPTED),
        reriskStatus = Some(UpdateStatus.ACCEPTED)
      )
      agencyDetailsUpdateStatus.toInitHipAmendPayload mustBe expected
    }
  }
