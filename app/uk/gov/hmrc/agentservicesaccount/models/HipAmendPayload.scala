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
) {

  private def withAmlsDetailsUpdate(amlsDetails: AmlsDetails): HipAmendPayload = {
    this.copy(
      supervisoryBody = Some(amlsDetails.supervisoryBody.value),
      membershipNumber = Some(amlsDetails.membershipNumber.value),
      evidenceObjectReference = amlsDetails.evidenceObjectReference.map(_.value)
    )
  }

  private def withAgencyDetailsUpdate(agencyDetails: AgencyDetails): HipAmendPayload = {
    val payloadWithNameEmailPhone = this.copy(
      name = agencyDetails.agencyName.orElse(name),
      email = agencyDetails.agencyEmail.orElse(email),
      phone = agencyDetails.agencyTelephone.orElse(phone)
    )
    agencyDetails.agencyAddress.map(agencyAddress => {
      payloadWithNameEmailPhone.copy(
        addr1 = Some(agencyAddress.addressLine1),
        addr2 = agencyAddress.addressLine2,
        addr3 = agencyAddress.addressLine3,
        addr4 = agencyAddress.addressLine4,
        postcode = agencyAddress.postalCode,
        country = Some(agencyAddress.countryCode)
      )
    }).getOrElse(payloadWithNameEmailPhone)
  }

}

object HipAmendPayload:

  given Writes[HipAmendPayload] = Json.writes[HipAmendPayload]

  extension (request: AgentRecordUpdateRequest)
    //  TODO: 11995 Remove logger
    def toHipAmendPayload(oldRecord: AgentDetailsResponse)(logger: Logger): HipAmendPayload =
      request match
        case AmlsUpdateRequest(update) => oldRecord.toInitHipAmendPayload.withAmlsDetailsUpdate(update)
        case AgencyDetailsUpdateRequest(update) => oldRecord.toInitHipAmendPayload.withAgencyDetailsUpdate(update)
