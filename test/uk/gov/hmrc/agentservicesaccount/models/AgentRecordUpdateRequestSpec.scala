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

import play.api.Logger
import play.api.libs.json.*
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.models.AmlsDetails.*
import uk.gov.hmrc.agentservicesaccount.models.HipAmendPayload.toHipAmendPayload
import uk.gov.hmrc.agentservicesaccount.models.UpdateStatus.ACCEPTED
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class AgentRecordUpdateRequestSpec
extends UnitSpec {

  val logger = Logger("")
  val oldRecord = AgentDetailsDesResponse(
    uniqueTaxReference = Some(Utr("1234567890")),
    agencyDetails = Some(AgencyDetails(
      agencyName = Some("Old Agency Name"),
      agencyEmail = Some("test@email.com"),
      agencyTelephone = Some("07123456789"),
      agencyAddress = Some(BusinessAddress(
        addressLine1 = "Old Address Line 1",
        addressLine2 = Some("Old Address Line 2"),
        addressLine3 = Some("Old Address Line 3"),
        postalCode = Some("AA1 1AA"),
        countryCode = "GB"
      ))
    )),
    amlsDetails = Some(AmlsDetails(
      supervisoryBody = SupervisoryBody("SRA"),
      membershipNumber = MembershipNumber("XAML00000123456"),
      evidenceObjectReference = Some(EvidenceObjectReference("old-ref-456"))
    )),
    suspensionDetails = None,
    isAnIndividual = None
  )

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
      request shouldBe AmlsUpdateRequest(
        AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456"),
          evidenceObjectReference = None
        )
      )
    }

    "parse with agencyDetails only" in {
      val json = Json.obj(
        "agencyDetails" -> Json.obj(
          "agencyName" -> "Test Agency",
          "agencyEmail" -> "test@example.com"
        )
      )
      val request = json.as[AgentRecordUpdateRequest]
      request shouldBe AgencyDetailsUpdateRequest(
        AgencyDetails(
          agencyName = Some("Test Agency"),
          agencyEmail = Some("test@example.com"),
          agencyTelephone = None,
          agencyAddress = None
        )
      )
    }

    "fail to parse with both sections" in {
      val json = Json.obj(
        "amlsDetails" -> Json.obj(
          "supervisoryBody" -> "SRA",
          "membershipNumber" -> "XAML00000123456"
        ),
        "agencyDetails" -> Json.obj(
          "agencyName" -> "Test Agency"
        )
      )
      val request = json.validate[AgentRecordUpdateRequest]
      request shouldBe a[JsError]
    }

    "fail to parse with neither section" in {
      val json = Json.obj()
      val request = json.validate[AgentRecordUpdateRequest]
      request shouldBe a[JsError]

    }

    "fail to parse with invalid json" in {
      val json = Json.obj(
        "amlsDetails" -> "invalid"
      )
      val request = json.validate[AgentRecordUpdateRequest]
      request shouldBe a[JsError]
    }
  }

  "toHipAmendPayload" should {

    val agencyDetails = AgencyDetails(
      agencyName = Some("Test Agency"),
      agencyEmail = Some("test@example.com"),
      agencyTelephone = Some("07123456789"),
      agencyAddress = Some(BusinessAddress(
        addressLine1 = "1 High Street",
        addressLine2 = Some("Floor 2"),
        postalCode = Some("TF1 1AA"),
        countryCode = "GB"
      ))
    )

    val amlsDetails = AmlsDetails(
      supervisoryBody = SupervisoryBody("SRA"),
      membershipNumber = MembershipNumber("XAML00000123456"),
      evidenceObjectReference = Some(EvidenceObjectReference("f28047ef-33f9-482e-a76a-ec4304de7b62"))
    )
    
    "when useUpdatedHipPutAgentRecord false" should {
      "map AMLS details correctly" in {
        val request = AmlsUpdateRequest(AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456"),
          evidenceObjectReference = Some(EvidenceObjectReference("ref-123"))
        ))

        val payload = request.toHipAmendPayload(oldRecord, false)(logger)
        payload.supervisoryBody shouldBe Some("SRA")
        payload.membershipNumber shouldBe Some("XAML00000123456")
        payload.evidenceObjectReference shouldBe Some("ref-123")

        payload.name shouldBe None
        payload.addr1 shouldBe None
        payload.amlSupervisionUpdateStatus shouldBe None
        payload.updateDetailsStatus shouldBe None
      }

      "map agency details correctly" in {
        val request = AgencyDetailsUpdateRequest(AgencyDetails(
          agencyName = Some("Test Agency"),
          agencyEmail = Some("test@example.com"),
          agencyTelephone = Some("07123456789"),
          agencyAddress = Some(BusinessAddress(
            addressLine1 = "1 High Street",
            addressLine2 = Some("Floor 2"),
            addressLine3 = Some("Town Centre"),
            postalCode = Some("TF1 1AA"),
            countryCode = "GB"
          ))
        ))

        val payload = request.toHipAmendPayload(oldRecord, false)(logger)
        payload.name shouldBe Some("Test Agency")
        payload.email shouldBe Some("test@example.com")
        payload.phone shouldBe Some("07123456789")
        payload.addr1 shouldBe Some("1 High Street")
        payload.addr2 shouldBe Some("Floor 2")
        payload.addr3 shouldBe Some("Town Centre")
        payload.addr4 shouldBe None
        payload.postcode shouldBe Some("TF1 1AA")
        payload.country shouldBe Some("GB")

        payload.supervisoryBody shouldBe None
        payload.updateDetailsStatus shouldBe None
        payload.amlSupervisionUpdateStatus shouldBe None
      }

      "fill in fallback values for optional address lines when old record has them defined but update request does not override them" in {
        val request = AgencyDetailsUpdateRequest(AgencyDetails(
          agencyName = Some("Test Agency"),
          agencyEmail = Some("test@example.com"),
          agencyTelephone = Some("07123456789"),
          agencyAddress = Some(BusinessAddress(
            addressLine1 = "1 High Street",
            addressLine2 = None,
            addressLine3 = None,
            addressLine4 = None,
            postalCode = None,
            countryCode = "EE"
          ))
        ))

        val payload = request.toHipAmendPayload(oldRecord, false)(logger)
        payload.name shouldBe Some("Test Agency")
        payload.email shouldBe Some("test@example.com")
        payload.phone shouldBe Some("07123456789")
        payload.addr1 shouldBe Some("1 High Street")
        payload.addr2 shouldBe Some("Address Line 2")
        payload.addr3 shouldBe Some("Address Line 3")
        payload.addr4 shouldBe None
        payload.postcode shouldBe Some("Postcode")
        payload.country shouldBe Some("EE")

        payload.supervisoryBody shouldBe None
        payload.updateDetailsStatus shouldBe None
        payload.amlSupervisionUpdateStatus shouldBe None
      }

      "not fill in fallback values for optional address lines when old record has them defined but update request does not change the address" in {
        val request = AgencyDetailsUpdateRequest(AgencyDetails(
          agencyName = None,
          agencyEmail = Some("test@example.com"),
          agencyTelephone = Some("07123456789"),
          agencyAddress = None
        ))

        val payload = request.toHipAmendPayload(oldRecord, false)(logger)
        payload.name shouldBe None
        payload.email shouldBe Some("test@example.com")
        payload.phone shouldBe Some("07123456789")
        payload.addr1 shouldBe None
        payload.addr2 shouldBe None
        payload.addr3 shouldBe None
        payload.addr4 shouldBe None
        payload.postcode shouldBe None
        payload.country shouldBe None

        payload.supervisoryBody shouldBe None
        payload.updateDetailsStatus shouldBe None
        payload.amlSupervisionUpdateStatus shouldBe None
      }

      def assertAmlsDetailsNoneInPayload(hipAmendPayload: HipAmendPayload): Unit = {
        hipAmendPayload.supervisoryBody shouldBe None
        hipAmendPayload.membershipNumber shouldBe None
        hipAmendPayload.evidenceObjectReference shouldBe None
      }

      def assertAgencyNameTelephoneEmailNoneInPayload(hipAmendPayload: HipAmendPayload): Unit = {
        hipAmendPayload.name shouldBe None
        hipAmendPayload.phone shouldBe None
        hipAmendPayload.email shouldBe None
      }

      def assertAgencyAddressNoneInPayload(hipAmendPayload: HipAmendPayload): Unit = {
        hipAmendPayload.addr1 shouldBe None
        hipAmendPayload.addr2 shouldBe None
        hipAmendPayload.addr3 shouldBe None
        hipAmendPayload.addr4 shouldBe None
        hipAmendPayload.postcode shouldBe None
        hipAmendPayload.country shouldBe None
      }

      def assertMMTARFieldsNoneInPayload(hipAmendPayload: HipAmendPayload): Unit = {
        hipAmendPayload.updateDetailsStatus shouldBe None
        hipAmendPayload.amlSupervisionUpdateStatus shouldBe None
        hipAmendPayload.directorPartnerUpdateStatus shouldBe None
        hipAmendPayload.acceptNewTermsStatus shouldBe None
        hipAmendPayload.reriskStatus shouldBe None
      }

      "return correct HipAmendPayload when passed AmlsUpdateRequest" in :
        val amlsUpdateRequest: AgentRecordUpdateRequest = AmlsUpdateRequest(amlsDetails)
        val hipAmendPayload = amlsUpdateRequest.toHipAmendPayload(oldRecord, false)(logger)

        hipAmendPayload.supervisoryBody shouldBe Some(amlsDetails.supervisoryBody.toString)
        hipAmendPayload.membershipNumber shouldBe Some(amlsDetails.membershipNumber.toString)
        hipAmendPayload.evidenceObjectReference shouldBe amlsDetails.evidenceObjectReference

        assertAgencyNameTelephoneEmailNoneInPayload(hipAmendPayload)
        assertAgencyAddressNoneInPayload(hipAmendPayload)
        assertMMTARFieldsNoneInPayload(hipAmendPayload)

      "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with name, phone, email only updated" in :
        val agencyDetailsNoAddress = agencyDetails.copy(agencyAddress = None)
        val agencyDetailsUpdateRequestNoAddress: AgentRecordUpdateRequest = AgencyDetailsUpdateRequest(agencyDetailsNoAddress)
        val hipAmendPayload = agencyDetailsUpdateRequestNoAddress.toHipAmendPayload(oldRecord, false)(logger)

        hipAmendPayload.name shouldBe agencyDetails.agencyName
        hipAmendPayload.email shouldBe agencyDetails.agencyEmail
        hipAmendPayload.phone shouldBe agencyDetails.agencyTelephone

        assertAmlsDetailsNoneInPayload(hipAmendPayload)
        assertAgencyAddressNoneInPayload(hipAmendPayload)
        assertMMTARFieldsNoneInPayload(hipAmendPayload)

      "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with address only updated" in :
        val agencyDetailsAddressOnly = AgencyDetails(None, None, None, agencyDetails.agencyAddress)
        val agencyDetailsUpdateRequestAddressOnly: AgentRecordUpdateRequest = AgencyDetailsUpdateRequest(agencyDetailsAddressOnly)
        val hipAmendPayload = agencyDetailsUpdateRequestAddressOnly.toHipAmendPayload(oldRecord, false)(logger)

        hipAmendPayload.addr1 shouldBe agencyDetails.agencyAddress.map(_.addressLine1)
        hipAmendPayload.addr2 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine2)
        hipAmendPayload.addr3 shouldBe Some("Address Line 3")
        hipAmendPayload.addr4 shouldBe None
        hipAmendPayload.postcode shouldBe agencyDetails.agencyAddress.flatMap(_.postalCode)
        hipAmendPayload.country shouldBe agencyDetails.agencyAddress.map(_.countryCode)

        assertAmlsDetailsNoneInPayload(hipAmendPayload)
        assertAgencyNameTelephoneEmailNoneInPayload(hipAmendPayload)
        assertMMTARFieldsNoneInPayload(hipAmendPayload)

      "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with all fields updated" in :
        val agencyDetailsUpdateRequest: AgentRecordUpdateRequest = AgencyDetailsUpdateRequest(agencyDetails)
        val hipAmendPayload = agencyDetailsUpdateRequest.toHipAmendPayload(oldRecord, false)(logger)

        hipAmendPayload.name shouldBe agencyDetails.agencyName
        hipAmendPayload.email shouldBe agencyDetails.agencyEmail
        hipAmendPayload.phone shouldBe agencyDetails.agencyTelephone
        hipAmendPayload.addr1 shouldBe agencyDetails.agencyAddress.map(_.addressLine1)
        hipAmendPayload.addr2 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine2)
        hipAmendPayload.addr3 shouldBe Some("Address Line 3")
        hipAmendPayload.addr4 shouldBe None
        hipAmendPayload.postcode shouldBe agencyDetails.agencyAddress.flatMap(_.postalCode)
        hipAmendPayload.country shouldBe agencyDetails.agencyAddress.map(_.countryCode)

        assertAmlsDetailsNoneInPayload(hipAmendPayload)
        assertMMTARFieldsNoneInPayload(hipAmendPayload)

      "omit None fields in JSON output" in {
        val request = AmlsUpdateRequest(AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456")
        ))

        val payload = request.toHipAmendPayload(oldRecord, false)(logger)

        val fields = Json.toJson(payload).as[JsObject].keys

        fields should contain("supervisoryBody")
        fields should contain("membershipNumber")
        fields should not contain "name"
        fields should not contain "addr1"
        fields should not contain "addr2"
        fields should not contain "addr3"
        fields should not contain "addr4"
        fields should not contain "postcode"
        fields should not contain "updateDetailsStatus"
        fields should not contain "amlSupervisionUpdateStatus"
        fields should not contain "directorPartnerUpdateStatus"
      }
    }

    "when useUpdatedHipPutAgentRecord true" should {

      def assertAmlsDetailsSameAsOldRecordInPayload(hipAmendPayload: HipAmendPayload, oldRecord: AgentDetailsDesResponse): Unit = {
        hipAmendPayload.supervisoryBody shouldBe oldRecord.amlsDetails.map(_.supervisoryBody)
        hipAmendPayload.membershipNumber shouldBe oldRecord.amlsDetails.map(_.membershipNumber)
        hipAmendPayload.evidenceObjectReference shouldBe oldRecord.amlsDetails.flatMap(_.evidenceObjectReference)
      }

      def assertAgencyNameTelephoneEmailSameAsOldRecordInPayload(hipAmendPayload: HipAmendPayload, oldRecord: AgentDetailsDesResponse): Unit = {
        hipAmendPayload.name shouldBe oldRecord.agencyDetails.flatMap(_.agencyName)
        hipAmendPayload.phone shouldBe oldRecord.agencyDetails.flatMap(_.agencyTelephone)
        hipAmendPayload.email shouldBe oldRecord.agencyDetails.flatMap(_.agencyEmail)
      }

      def assertAgencyAddressSameAsOldRecordInPayload(hipAmendPayload: HipAmendPayload, oldRecord: AgentDetailsDesResponse): Unit = {
        hipAmendPayload.addr1 shouldBe oldRecord.agencyDetails.flatMap(_.agencyAddress).map(_.addressLine1)
        hipAmendPayload.addr2 shouldBe oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine2)
        hipAmendPayload.addr3 shouldBe oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine3)
        hipAmendPayload.addr4 shouldBe oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine4)
        hipAmendPayload.postcode shouldBe oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.postalCode)
        hipAmendPayload.country shouldBe oldRecord.agencyDetails.flatMap(_.agencyAddress).map(_.countryCode)
      }

      def assertMMTARFieldsSetAsAcceptedInPayload(hipAmendPayload: HipAmendPayload): Unit = {
        hipAmendPayload.updateDetailsStatus shouldBe Some(ACCEPTED)
        hipAmendPayload.amlSupervisionUpdateStatus shouldBe Some(ACCEPTED)
        hipAmendPayload.directorPartnerUpdateStatus shouldBe Some(ACCEPTED)
        hipAmendPayload.acceptNewTermsStatus shouldBe Some(ACCEPTED)
        hipAmendPayload.reriskStatus shouldBe Some(ACCEPTED)
      }

      "return correct HipAmendPayload when passed AmlsUpdateRequest" in :
        val agentRecordUpdateRequest: AgentRecordUpdateRequest = AmlsUpdateRequest(amlsDetails)
        val hipAmendPayload = agentRecordUpdateRequest.toHipAmendPayload(oldRecord, true)(logger)

        hipAmendPayload.supervisoryBody shouldBe Some(amlsDetails.supervisoryBody.toString)
        hipAmendPayload.membershipNumber shouldBe Some(amlsDetails.membershipNumber.toString)
        hipAmendPayload.evidenceObjectReference shouldBe amlsDetails.evidenceObjectReference

        assertAgencyNameTelephoneEmailSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertAgencyAddressSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertMMTARFieldsSetAsAcceptedInPayload(hipAmendPayload)

      "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with name, phone, email only updated" in :
        val agencyDetailsNoAddress = agencyDetails.copy(agencyAddress = None)
        val agencyDetailsUpdateRequestNoAddress: AgentRecordUpdateRequest = AgencyDetailsUpdateRequest(agencyDetailsNoAddress)
        val hipAmendPayload = agencyDetailsUpdateRequestNoAddress.toHipAmendPayload(oldRecord, true)(logger)

        hipAmendPayload.name shouldBe agencyDetails.agencyName
        hipAmendPayload.email shouldBe agencyDetails.agencyEmail
        hipAmendPayload.phone shouldBe agencyDetails.agencyTelephone

        assertAmlsDetailsSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertAgencyAddressSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertMMTARFieldsSetAsAcceptedInPayload(hipAmendPayload)

      "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with address only updated" in :
        val agencyDetailsAddressOnly = AgencyDetails(None, None, None, agencyDetails.agencyAddress)
        val agencyDetailsUpdateRequestAddressOnly: AgentRecordUpdateRequest = AgencyDetailsUpdateRequest(agencyDetailsAddressOnly)
        val hipAmendPayload = agencyDetailsUpdateRequestAddressOnly.toHipAmendPayload(oldRecord, true)(logger)

        hipAmendPayload.addr1 shouldBe agencyDetails.agencyAddress.map(_.addressLine1)
        hipAmendPayload.addr2 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine2)
        hipAmendPayload.addr3 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine3)
        hipAmendPayload.addr4 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine4)
        hipAmendPayload.postcode shouldBe agencyDetails.agencyAddress.flatMap(_.postalCode)
        hipAmendPayload.country shouldBe agencyDetails.agencyAddress.map(_.countryCode)

        assertAmlsDetailsSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertAgencyNameTelephoneEmailSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertMMTARFieldsSetAsAcceptedInPayload(hipAmendPayload)

      "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with all fields updated" in :
        val agencyDetailsUpdateRequest: AgentRecordUpdateRequest = AgencyDetailsUpdateRequest(agencyDetails)
        val hipAmendPayload = agencyDetailsUpdateRequest.toHipAmendPayload(oldRecord, true)(logger)

        hipAmendPayload.name shouldBe agencyDetails.agencyName
        hipAmendPayload.email shouldBe agencyDetails.agencyEmail
        hipAmendPayload.phone shouldBe agencyDetails.agencyTelephone
        hipAmendPayload.addr1 shouldBe agencyDetails.agencyAddress.map(_.addressLine1)
        hipAmendPayload.addr2 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine2)
        hipAmendPayload.addr3 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine3)
        hipAmendPayload.addr4 shouldBe agencyDetails.agencyAddress.flatMap(_.addressLine4)
        hipAmendPayload.postcode shouldBe agencyDetails.agencyAddress.flatMap(_.postalCode)
        hipAmendPayload.country shouldBe agencyDetails.agencyAddress.map(_.countryCode)

        assertAmlsDetailsSameAsOldRecordInPayload(hipAmendPayload, oldRecord)
        assertMMTARFieldsSetAsAcceptedInPayload(hipAmendPayload)

      "should only have None fields in JSON output where old record has None fields" in {
        val request = AmlsUpdateRequest(AmlsDetails(
          supervisoryBody = SupervisoryBody("SRA"),
          membershipNumber = MembershipNumber("XAML00000123456")
        ))

        val payload = request.toHipAmendPayload(oldRecord, true)(logger)

        val fields = Json.toJson(payload).as[JsObject].keys

        fields should contain("supervisoryBody")
        fields should contain("membershipNumber")
        fields should contain("name")
        fields should contain("addr1")
        fields should contain("addr2")
        fields should contain("addr3")
        fields should not contain "addr4"
        fields should contain("postcode")
        fields should contain("updateDetailsStatus")
        fields should contain("amlSupervisionUpdateStatus")
        fields should contain("directorPartnerUpdateStatus")
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
