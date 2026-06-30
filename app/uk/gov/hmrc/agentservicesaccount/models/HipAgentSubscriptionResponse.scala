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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

case class HipAgentSubscriptionResponse(
  success: HipAgentSubscriptionSuccess
)

case class HipAgentSubscriptionSuccess(
  processingDate: String,
  utr: Option[String],
  name: String,
  addr1: String,
  addr2: Option[String],
  addr3: Option[String],
  addr4: Option[String],
  postcode: Option[String],
  country: String,
  phone: Option[String],
  email: String,
  suspensionStatus: String,
  regime: Option[Seq[String]],
  supervisoryBody: Option[String],
  membershipNumber: Option[String],
  evidenceObjectReference: Option[String],
  updateDetailsStatus: Option[UpdateStatus],
  amlSupervisionUpdateStatus: Option[UpdateStatus],
  directorPartnerUpdateStatus: Option[UpdateStatus],
  acceptNewTermsStatus: Option[UpdateStatus],
  reriskStatus: Option[UpdateStatus]
)

object HipAgentSubscriptionResponse {

  private def readNullableString(path: JsPath): Reads[Option[String]] = path.readNullable[String].map {
    case Some(s) if s.trim.isEmpty => None
    case other => other
  }

  private val utrReads: Reads[Option[String]] = (__ \ "utr").readNullable[JsValue].map {
    case Some(JsNumber(n)) => Some(n.toString)
    case Some(JsString(s)) if s.trim.nonEmpty => Some(s)
    case _ => None
  }

  private val regimeReads: Reads[Option[Seq[String]]] = (__ \ "regime").readNullable[JsValue].flatMap {
    case None => Reads.pure(None)
    case Some(JsString(regime)) => Reads.pure(Some(Seq(regime).filter(_.trim.nonEmpty)))
    case Some(array: JsArray) => Reads(_ => array.validate[Seq[String]].map(regimes => Some(regimes.filter(_.trim.nonEmpty))))
    case Some(_) => Reads(_ => JsError(__ \ "regime", JsonValidationError("error.expected.jsstringorjsarray")))
  }

  given Reads[HipAgentSubscriptionSuccess] =
    (
      (__ \ "processingDate").read[String] and
        utrReads and
        (__ \ "name").read[String] and
        (__ \ "addr1").read[String] and
        readNullableString(__ \ "addr2").map(_.filterNot(_ == "Address Line 2")) and // Filtering out autofilled default values
        readNullableString(__ \ "addr3").map(_.filterNot(_ == "Address Line 3")) and // Filtering out autofilled default values
        readNullableString(__ \ "addr4").map(_.filterNot(_ == "Address Line 4")) and // Filtering out autofilled default values
        readNullableString(__ \ "postcode").map(_.filterNot(_ == "Postcode")) and // Filtering out autofilled default values
        (__ \ "country").read[String] and
        readNullableString(__ \ "phone") and
        (__ \ "email").read[String] and
        (__ \ "suspensionStatus").read[String] and
        regimeReads and
        readNullableString(__ \ "supervisoryBody") and
        readNullableString(__ \ "membershipNumber") and
        readNullableString(__ \ "evidenceObjectReference") and
        (__ \ "updateDetailsStatus").readNullable[UpdateStatus] and
        (__ \ "amlSupervisionUpdateStatus").readNullable[UpdateStatus] and
        (__ \ "directorPartnerUpdateStatus").readNullable[UpdateStatus] and
        (__ \ "acceptNewTermsStatus").readNullable[UpdateStatus] and
        (__ \ "reriskStatus").readNullable[UpdateStatus]
    )(HipAgentSubscriptionSuccess.apply)

  given Reads[HipAgentSubscriptionResponse] = (__ \ "success").read[HipAgentSubscriptionSuccess]
    .map(HipAgentSubscriptionResponse.apply)

}
