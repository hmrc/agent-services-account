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

import play.api.libs.json.*
import uk.gov.hmrc.agentservicesaccount.models.AmlsDetails.*
import uk.gov.hmrc.agentservicesaccount.models.HipAmendPayload.toHipAmendPayload
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class AgentRecordUpdateRequestSpec extends UnitSpec {

  "UpdateStatus" should {
    "serialise to JSON strings" in {
      Json.toJson(UpdateStatus.ACCEPTED) shouldBe JsString("ACCEPTED")
      Json.toJson(UpdateStatus.REJECTED) shouldBe JsString("REJECTED")
      Json.toJson(UpdateStatus.PENDING) shouldBe JsString("PENDING")
      Json.toJson(UpdateStatus.REQUIRED) shouldBe JsString("REQUIRED")
    }

    "deserialise from JSON strings" in {
      JsString("ACCEPTED").as[UpdateStatus] shouldBe UpdateStatus.ACCEPTED
      JsString("REQUIRED").as[UpdateStatus] shouldBe UpdateStatus.REQUIRED
    }

    "fail to deserialise unknown values" in {
      JsString("UNKNOWN").validate[UpdateStatus] shouldBe a[JsError]
    }
  }

  "AmlsDetails" should {
    "serialise and deserialise with all fields" in {
      val amls = AmlsDetails(
        supervisoryBody = SupervisoryBody("SRA"),
        membershipNumber = MembershipNumber("XAML00000123456"),
        evidenceObjectReference = Some(EvidenceObjectReference("f28047ef-33f9-482e-a76a-ec4304de7b62"))
      )
      val json = Json.toJson(amls)
      (json \ "supervisoryBody").as[String] shouldBe "SRA"
      (json \ "membershipNumber").as[String] shouldBe "XAML00000123456"
      (json \ "evidenceObjectReference").as[String] shouldBe "f28047ef-33f9-482e-a76a-ec4304de7b62"
      json.as[AmlsDetails] shouldBe amls
    }

    "serialise and deserialise without evidenceObjectReference" in {
      val amls = AmlsDetails(
        supervisoryBody = SupervisoryBody("SRA"),
        membershipNumber = MembershipNumber("XAML00000123456")
      )
      val json = Json.toJson(amls)
      (json \ "evidenceObjectReference").toOption shouldBe None
      json.as[AmlsDetails] shouldBe amls
    }
  }

  "AgentRecordUpdateRequest" should {
    "parse with amlsDetails only" in {
      val json = Json.obj(
        "amlsDetails" -> Json.obj(
          "supervisoryBody" -> "SRA",
          "membershipNumber" -> "XAML00000123456"
        )
      )
      val request = json.as[AgentRecordUpdateRequest]
      request.amlsDetails shouldBe defined
      request.agencyDetails shouldBe empty
    }

    "parse with agencyDetails only" in {
      val json = Json.obj(
        "agencyDetails" -> Json.obj(
          "agencyName" -> "Test Agency",
          "agencyEmail" -> "test@example.com"
        )
      )
      val request = json.as[AgentRecordUpdateRequest]
      request.amlsDetails shouldBe empty
      request.agencyDetails shouldBe defined
    }

    "parse with both sections" in {
      val json = Json.obj(
        "amlsDetails" -> Json.obj(
          "supervisoryBody" -> "SRA",
          "membershipNumber" -> "XAML00000123456"
        ),
        "agencyDetails" -> Json.obj(
          "agencyName" -> "Test Agency"
        )
      )
      val request = json.as[AgentRecordUpdateRequest]
      request.amlsDetails shouldBe defined
      request.agencyDetails shouldBe defined
    }

    "parse with neither section" in {
      val json = Json.obj()
      val request = json.as[AgentRecordUpdateRequest]
      request.amlsDetails shouldBe empty
      request.agencyDetails shouldBe empty
    }
  }

  "toHipAmendPayload" should {
    "map AMLS details correctly" in {
      val request = AgentRecordUpdateRequest(
        amlsDetails = Some(AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456"),
          evidenceObjectReference = Some(EvidenceObjectReference("ref-123"))
        )),
        agencyDetails = None
      )

      request.toHipAmendPayload match
        case Right(payload) =>
          payload.supervisoryBody shouldBe Some("SRA")
          payload.membershipNumber shouldBe Some("XAML00000123456")
          payload.evidenceObjectReference shouldBe Some("ref-123")
          payload.amlSupervisionUpdateStatus shouldBe Some(UpdateStatus.ACCEPTED)

          payload.name shouldBe None
          payload.addr1 shouldBe None
          payload.updateDetailsStatus shouldBe None
        case _ =>
          fail()



    }

    "map agency details correctly" in {
      val request = AgentRecordUpdateRequest(
        amlsDetails = None,
        agencyDetails = Some(AgencyDetails(
          agencyName = Some("Test Agency"),
          agencyEmail = Some("test@example.com"),
          agencyTelephone = Some("07123456789"),
          agencyAddress = Some(BusinessAddress(
            addressLine1 = "1 High Street",
            addressLine2 = Some("Floor 2"),
            addressLine3 = Some("Town Centre"),
            addressLine4 = Some("Telford"),
            postalCode = Some("TF1 1AA"),
            countryCode = "GB"
          ))
        ))
      )

      request.toHipAmendPayload match {
        case Right(payload) =>
          payload.name shouldBe Some("Test Agency")
          payload.email shouldBe Some("test@example.com")
          payload.phone shouldBe Some("07123456789")
          payload.addr1 shouldBe Some("1 High Street")
          payload.addr2 shouldBe Some("Floor 2")
          payload.addr3 shouldBe Some("Town Centre")
          payload.addr4 shouldBe Some("Telford")
          payload.postcode shouldBe Some("TF1 1AA")
          payload.country shouldBe Some("GB")
          payload.updateDetailsStatus shouldBe Some(UpdateStatus.ACCEPTED)

          payload.supervisoryBody shouldBe None
          payload.amlSupervisionUpdateStatus shouldBe None

        case _ =>
          fail()
      }
    }

    "return Left when both sections are provided" in {
      val request = AgentRecordUpdateRequest(
        amlsDetails = Some(AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456")
        )),
        agencyDetails = Some(AgencyDetails(
          agencyName = Some("Test Agency"),
          agencyEmail = Some("test@example.com"),
          agencyTelephone = None,
          agencyAddress = None
        ))
      )

      request.toHipAmendPayload shouldBe a[Left[?, ?]]
      request.toHipAmendPayload.left.foreach(_ should include("cannot both"))
    }

    "return Left when neither section is provided" in {
      val request = AgentRecordUpdateRequest(
        amlsDetails = None,
        agencyDetails = None
      )

      request.toHipAmendPayload shouldBe a[Left[?, ?]]
    }

    "omit None fields in JSON output" in {
      val request = AgentRecordUpdateRequest(
        amlsDetails = Some(AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456")
        )),
        agencyDetails = None
      )

      request.toHipAmendPayload.map(Json.toJson(_)) match {
        case Right(json) =>
          val fields = json.as[JsObject].keys

          fields should contain("supervisoryBody")
          fields should contain("membershipNumber")
          fields should contain("amlSupervisionUpdateStatus")
          fields should not contain "name"
          fields should not contain "addr1"
          fields should not contain "updateDetailsStatus"
          fields should not contain "directorPartnerUpdateStatus"

        case _ =>
          fail()
      }

    }
  }

  "HipAmendResponse" should {
    "deserialise from HIP success response" in {
      val json = Json.parse("""{"success":{"processingDate":"2024-07-15T09:30:47Z"}}""")
      val response = json.as[HipAmendResponse]
      response.success.processingDate shouldBe "2024-07-15T09:30:47Z"
    }
  }
}
