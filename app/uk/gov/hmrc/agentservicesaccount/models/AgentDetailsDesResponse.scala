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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.models.UpdateStatus.ACCEPTED
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

case class AgentDetailsDesResponse(
  uniqueTaxReference: Option[Utr],
  agencyDetails: Option[AgencyDetails],
  suspensionDetails: Option[SuspensionDetails],
  isAnIndividual: Option[Boolean],
  amlsDetails: Option[AmlsDetails] = None,
  updateDetailsStatus: Option[String] = None,
  amlSupervisionUpdateStatus: Option[String] = None,
  directorPartnerUpdateStatus: Option[String] = None,
  acceptNewTermsStatus: Option[String] = None,
  reriskStatus: Option[String] = None
) {
  private[models] def toInitHipAmendPayload: HipAmendPayload = {
    HipAmendPayload(
      name = agencyDetails.flatMap(_.agencyName),
      addr1 = agencyDetails.flatMap(_.agencyAddress).map(_.addressLine1),
      addr2 = agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine2),
      addr3 = agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine3),
      addr4 = agencyDetails.flatMap(_.agencyAddress).flatMap(_.addressLine4),
      postcode = agencyDetails.flatMap(_.agencyAddress).flatMap(_.postalCode),
      country = agencyDetails.flatMap(_.agencyAddress).map(_.countryCode),
      phone = agencyDetails.flatMap(_.agencyTelephone),
      email = agencyDetails.flatMap(_.agencyEmail),
      supervisoryBody = amlsDetails.map(_.supervisoryBody.toString),
      membershipNumber = amlsDetails.map(_.membershipNumber.toString),
      evidenceObjectReference = amlsDetails.flatMap(_.evidenceObjectReference).map(_.toString),
      updateDetailsStatus = Some(updateDetailsStatus.map(_.asInstanceOf[UpdateStatus]).getOrElse(ACCEPTED)),
      amlSupervisionUpdateStatus =  Some(amlSupervisionUpdateStatus.map(_.asInstanceOf[UpdateStatus]).getOrElse(ACCEPTED)),
      directorPartnerUpdateStatus =  Some(directorPartnerUpdateStatus.map(_.asInstanceOf[UpdateStatus]).getOrElse(ACCEPTED)),
      acceptNewTermsStatus =  Some(acceptNewTermsStatus.map(_.asInstanceOf[UpdateStatus]).getOrElse(ACCEPTED)),
      reriskStatus = Some(reriskStatus.map(_.asInstanceOf[UpdateStatus]).getOrElse(ACCEPTED))
    )
  }
}

object AgentDetailsDesResponse {

  given agentRecordDetailsFormat: OFormat[AgentDetailsDesResponse] = Json.format[AgentDetailsDesResponse]

  def agentRecordDatabaseDetailsFormat(using crypto: Encrypter & Decrypter): Format[AgentDetailsDesResponse] =
    (__ \ "uniqueTaxReference")
      .formatNullable[String](using stringEncrypterDecrypter)
      .bimap[Option[Utr]](
        _.map(Utr(_)),
        _.map(_.value)
      )
      .and((__ \ "agencyDetails").formatNullable[AgencyDetails](using AgencyDetails.agencyDetailsDatabaseFormat))
      .and((__ \ "suspensionDetails").formatNullable[SuspensionDetails])
      .and((__ \ "isAnIndividual").formatNullable[Boolean])
      .and((__ \ "amlsDetails").formatNullable[AmlsDetails](using AmlsDetails.amlsDetailsDatabaseFormat))
      .and((__ \ "updateDetailsStatus").formatNullable[String])
      .and((__ \ "amlSupervisionUpdateStatus").formatNullable[String])
      .and((__ \ "directorPartnerUpdateStatus").formatNullable[String])
      .and((__ \ "acceptNewTermsStatus").formatNullable[String])
      .and((__ \ "reriskStatus").formatNullable[String])(
        AgentDetailsDesResponse.apply,
        adr => (adr.uniqueTaxReference, adr.agencyDetails, adr.suspensionDetails, adr.isAnIndividual, adr.amlsDetails, adr.updateDetailsStatus, adr.amlSupervisionUpdateStatus, adr.directorPartnerUpdateStatus, adr.acceptNewTermsStatus, adr.reriskStatus)
      )

}
