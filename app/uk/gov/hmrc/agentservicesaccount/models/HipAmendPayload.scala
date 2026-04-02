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

object HipAmendPayload:

  given Writes[HipAmendPayload] = Json.writes[HipAmendPayload]

  extension (request: AgentRecordUpdateRequest)
    def toHipAmendPayload: Either[String, HipAmendPayload] =
      def agencyFields(ad: AgencyDetails) =
        HipAmendPayload(
          name = ad.agencyName,
          addr1 = ad.agencyAddress.map(_.addressLine1),
          addr2 = ad.agencyAddress.flatMap(_.addressLine2),
          addr3 = ad.agencyAddress.flatMap(_.addressLine3),
          addr4 = ad.agencyAddress.flatMap(_.addressLine4),
          postcode = ad.agencyAddress.flatMap(_.postalCode),
          country = ad.agencyAddress.map(_.countryCode),
          phone = ad.agencyTelephone,
          email = ad.agencyEmail
        )

      def amlsFields(amls: AmlsDetails) =
        HipAmendPayload(
          supervisoryBody = Some(amls.supervisoryBody.value),
          membershipNumber = Some(amls.membershipNumber.value),
          evidenceObjectReference = amls.evidenceObjectReference.map(_.value)
        )

      (request.agencyDetails, request.amlsDetails) match
        case (Some(agencyDetails), None) =>
          Right(agencyFields(agencyDetails))
        case (None, Some(amlsDetails)) =>
          Right(amlsFields(amlsDetails))
        case (None, None) =>
          Left("Either agencyDetails or amlsDetails must be provided")
        case _ =>
          Left("agencyDetails and amlsDetails cannot both be provided")
