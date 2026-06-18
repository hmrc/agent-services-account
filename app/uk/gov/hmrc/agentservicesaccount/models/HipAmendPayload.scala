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
import uk.gov.hmrc.agentservicesaccount.models.UpdateStatus.ACCEPTED

case class HipAmendPayload(
  name: Option[String] = None,
  addr1: Option[String] = None,
  addr2: Option[String] = None,
  addr3: Option[String] = None,
  addr4: Option[String] = None,
  postcode: Option[String] = None,
  country: Option[String] = None,
  phone: Option[String] = None,
  email: Option[String] = None,
  supervisoryBody: Option[String] = None,
  membershipNumber: Option[String] = None,
  evidenceObjectReference: Option[String] = None,
  updateDetailsStatus: Option[UpdateStatus] = None,
  amlSupervisionUpdateStatus: Option[UpdateStatus] = None,
  directorPartnerUpdateStatus: Option[UpdateStatus] = None,
  acceptNewTermsStatus: Option[UpdateStatus] = None,
  reriskStatus: Option[UpdateStatus] = None
)

private def toInitHipAmendPayload(oldRecord: AgentDetailsDesResponse): HipAmendPayload = {
  HipAmendPayload(
    name = oldRecord.agencyDetails.flatMap(_.agencyName),
    addr1 = oldRecord.agencyDetails.flatMap(_.agencyAddress).map(_.addressLine1),
    addr2 = oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine2),
    addr3 = oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine3),
    addr4 = oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine4),
    postcode = oldRecord.agencyDetails.flatMap(_.agencyAddress).flatMap(_.postalCode),
    country = oldRecord.agencyDetails.flatMap(_.agencyAddress).map(_.countryCode),
    phone = oldRecord.agencyDetails.flatMap(_.agencyTelephone),
    email = oldRecord.agencyDetails.flatMap(_.agencyEmail),
    supervisoryBody = oldRecord.amlsDetails.map(_.supervisoryBody),
    membershipNumber = oldRecord.amlsDetails.map(_.membershipNumber),
    evidenceObjectReference = oldRecord.amlsDetails.flatMap(_.evidenceObjectReference),
    updateDetailsStatus = Some(ACCEPTED),
    amlSupervisionUpdateStatus = Some(ACCEPTED),
    directorPartnerUpdateStatus = Some(ACCEPTED),
    acceptNewTermsStatus = Some(ACCEPTED),
    reriskStatus = Some(ACCEPTED)
  )
}

private def addAgencyDetailsUpdateToInitHipAmendPayload(details: AgencyDetails, initPayload: HipAmendPayload): HipAmendPayload = {
//  TODO: 11584 Implement
  val newNameRequired = details.agencyName.isDefined
  val newEmailRequired = details.agencyEmail.isDefined
  val newPhoneRequired = details.agencyTelephone.isDefined
  val newAddressRequired = details.agencyAddress.isDefined
  val nameToSend = initPayload.name
  val emailToSend = initPayload.email
  val phoneToSend = initPayload.phone
  val addressToSend = BusinessAddress(
    "Address Line 1",
    Some("Address Line 2"),
    Some("Address Line 3"),
    Some("Address Line 4"),
    Some("Post Code"),
    "Country Code"
  )
  initPayload.copy(
    name = nameToSend,
    email = emailToSend,
    phone = phoneToSend,
    addr1 = Some(addressToSend.addressLine1),
    addr2 = addressToSend.addressLine2,
    addr3 = addressToSend.addressLine3,
    addr4 = addressToSend.addressLine4,
    postcode = addressToSend.postalCode,
    country = Some(addressToSend.countryCode)
  )
}

object HipAmendPayload:

  given Writes[HipAmendPayload] = Json.writes[HipAmendPayload]

  extension (request: AgentRecordUpdateRequest)
    def toHipAmendPayload(oldRecord: AgentDetailsDesResponse, useUpdatedHipPutAgentRecord: Boolean = false)(logger: Logger): HipAmendPayload =
      def addressLineWithFallback(
        newLine: Option[String],
        oldLine: Option[String],
        fallback: String
      ): Option[String] = {
        if newLine.nonEmpty then newLine
        else if oldLine.exists(_.nonEmpty) then // If it's an empty string in the record we don't want to touch it.
          logger.warn(s"[HipAmendPayload] old record has optional field defined but update request is not overriding it. Using fallback '$fallback' to force override.")
          Some(fallback)
        else None
      }

      (request, useUpdatedHipPutAgentRecord) match
        case (AmlsUpdateRequest(update), true) =>
          val initHipAmendPayload = toInitHipAmendPayload(oldRecord)
          initHipAmendPayload.copy(
            supervisoryBody = Some(update.supervisoryBody.value),
            membershipNumber = Some(update.membershipNumber.value),
            evidenceObjectReference = update.evidenceObjectReference.map(_.value)
          )
        case (AgencyDetailsUpdateRequest(update), true) =>
          val initHipAmendPayload = toInitHipAmendPayload(oldRecord)
          addAgencyDetailsUpdateToInitHipAmendPayload(update, initHipAmendPayload)
        case (AmlsUpdateRequest(update), false) =>
          HipAmendPayload(
            supervisoryBody = Some(update.supervisoryBody.value),
            membershipNumber = Some(update.membershipNumber.value),
            evidenceObjectReference = update.evidenceObjectReference.map(_.value)
          )
        case (AgencyDetailsUpdateRequest(update), false) =>
          HipAmendPayload(
            name = update.agencyName,
            addr1 = update.agencyAddress.map(_.addressLine1),
            addr2 = update.agencyAddress.flatMap(newAddr =>
              addressLineWithFallback(
                newAddr.addressLine2,
                oldRecord.agencyDetails.flatMap(_.agencyAddress.flatMap(_.addressLine2)),
                "Address Line 2"
              )
            ),
            addr3 = update.agencyAddress.flatMap(newAddr =>
              addressLineWithFallback(
                newAddr.addressLine3,
                oldRecord.agencyDetails.flatMap(_.agencyAddress.flatMap(_.addressLine3)),
                "Address Line 3"
              )
            ),
            addr4 = update.agencyAddress.flatMap(newAddr =>
              addressLineWithFallback(
                newAddr.addressLine4,
                oldRecord.agencyDetails.flatMap(_.agencyAddress.flatMap(_.addressLine4)),
                "Address Line 4"
              )
            ),
            postcode = update.agencyAddress.flatMap(newAddr =>
              addressLineWithFallback(
                newAddr.postalCode,
                oldRecord.agencyDetails.flatMap(_.agencyAddress.flatMap(_.postalCode)),
                "Postcode"
              )
            ),
            country = update.agencyAddress.map(_.countryCode),
            phone = update.agencyTelephone,
            email = update.agencyEmail
          )
