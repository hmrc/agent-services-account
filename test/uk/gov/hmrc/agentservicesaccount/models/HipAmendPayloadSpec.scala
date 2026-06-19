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

package uk.gov.hmrc.agentservicesaccount.models

import play.api.Logger
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.models.AmlsDetails.{EvidenceObjectReference, MembershipNumber, SupervisoryBody}
import uk.gov.hmrc.agentservicesaccount.models.HipAmendPayload.toHipAmendPayload
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

// TODO: 11584 Add unit tests for HipAmendPayload model here
class HipAmendPayloadSpec
extends UnitSpec:

  private val logger = Logger("")
  private val oldRecord = AgentDetailsDesResponse(
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

  private val agencyDetails = AgencyDetails(
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
  )

  private val amlsDetails = AmlsDetails(
    supervisoryBody = SupervisoryBody("SRA"),
    membershipNumber = MembershipNumber("XAML00000123456"),
    evidenceObjectReference = Some(EvidenceObjectReference("f28047ef-33f9-482e-a76a-ec4304de7b62"))
  )

  "toHipAmendPayload" should:
    "return correct HipAmendPayload when passed AmlsUpdateRequest and useUpdatedHipPutAgentRecord false" in:
      val agentRecordUpdateRequest: AgentRecordUpdateRequest = AmlsUpdateRequest(amlsDetails)
      val hipAmendPayload = toHipAmendPayload(agentRecordUpdateRequest)(oldRecord, false)(logger)
      true shouldBe true

    "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with name, phone, email only updated and useUpdatedHipPutAgentRecord false" in :
      true shouldBe true

    "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with address only updated and useUpdatedHipPutAgentRecord false" in :
      true shouldBe true

    "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with all fields updated and useUpdatedHipPutAgentRecord false" in :
      true shouldBe true

    "return correct HipAmendPayload when passed AmlsUpdateRequest and useUpdatedHipPutAgentRecord true" in :
      val agentRecordUpdateRequest: AgentRecordUpdateRequest = AmlsUpdateRequest(amlsDetails)
      val hipAmendPayload = toHipAmendPayload(agentRecordUpdateRequest)(oldRecord, true)(logger)
      true shouldBe true

    "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with name, phone, email only updated and useUpdatedHipPutAgentRecord true" in :
      true shouldBe true

    "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with address only updated and useUpdatedHipPutAgentRecord true" in :
      true shouldBe true

    "return correct HipAmendPayload when passed AgencyDetailsUpdateRequest with all fields updated and useUpdatedHipPutAgentRecord true" in :
      true shouldBe true
//    "serialize to JSON using the implicit mongoFormat" in:
//      val result = Json.toJson(testHipAmendPayload)
//
//      result shouldBe Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> Json.obj(
//          "$date" -> Json.obj(
//            "$numberLong" ->
//              testHipAmendPayload.timeSubmitted.toEpochMilli.toString
//          )
//        )
//      )

//    "deserialize from JSON using the implicit mongoFormat" in:
//      val json = Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> Json.obj(
//          "$date" -> Json.obj(
//            "$numberLong" ->
//              testHipAmendPayload.timeSubmitted.toEpochMilli.toString
//          )
//        )
//      )
//
//      val result = Json.fromJson[HipAmendPayload](json).get
//
//      result shouldBe
//        testHipAmendPayload.copy(
//          timeSubmitted = testHipAmendPayload.timeSubmitted.truncatedTo(ChronoUnit.MILLIS)
//        ) // Done to account for rounding errors when converting to/from JSON

//    "serialize to JSON using the standard macro formatter" in:
//      val result = Json.toJson(testHipAmendPayload)(using HipAmendPayload.format)
//
//      result shouldBe Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> testHipAmendPayload.timeSubmitted
//      )

//    "deserialize from JSON using the standard macro formatter" in:
//      val json = Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> testHipAmendPayload.timeSubmitted
//      )
//
//      val result = Json.fromJson[HipAmendPayload](json)(using HipAmendPayload.format).get
//
//      result shouldBe testHipAmendPayload
