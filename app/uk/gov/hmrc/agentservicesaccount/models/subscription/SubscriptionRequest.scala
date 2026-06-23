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

package uk.gov.hmrc.agentservicesaccount.models.subscription

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*

// TODO since the models are the same they can be merged
sealed trait SubscriptionRequest:

  val agentName: String
  val contactName: String
  val phoneNumber: Option[String]
  val emailAddress: Option[String]
  val address: SubscriptionAddress
  val isAbroad: Boolean
  val isWelsh: Boolean

object SubscriptionRequest:

  def reads(regime: LegacyRegime): Reads[SubscriptionRequest] = Reads { json =>
    regime match {
      case PAYE => json.validate[PayeSubscriptionRequest]
      case SA   => json.validate[SaSubscriptionRequest]
      case CT   => json.validate[CtSubscriptionRequest]
    } match {
      case JsSuccess(request: SubscriptionRequest, _) if !request.isAbroad && request.address.postCode.isEmpty =>
        JsError("Postcode is required for legacy subscriptions in UK")
      case other => other
    }
  }

  def requestReads(regime: LegacyRegime): Reads[SubscriptionRequest] =
    reads(regime).filter(JsonValidationError("Postcode is required for legacy subscriptions in UK")) { request =>
      request.isAbroad || request.address.postCode.forall(_.trim.nonEmpty)
    }

  given Writes[SubscriptionRequest] = Writes {
    case payeRequest: PayeSubscriptionRequest => Json.toJson(payeRequest)
    case saRequest: SaSubscriptionRequest => Json.toJson(saRequest)
    case ctRequest: CtSubscriptionRequest => Json.toJson(ctRequest)
  }

case class PayeSubscriptionRequest(
  agentName: String,
  contactName: String,
  phoneNumber: Option[String],
  emailAddress: Option[String],
  address: SubscriptionAddress,
  isWelsh: Boolean
)
extends SubscriptionRequest:
  val isAbroad: Boolean = false // Unused value for PAYE as postcode is always required

object PayeSubscriptionRequest:

  given Reads[PayeSubscriptionRequest] =
    (
      (__ \ "agentName").read[String] and
        (__ \ "contactName").read[String] and
        (__ \ "phoneNumber").readNullable[String] and
        (__ \ "emailAddress").readNullable[String] and
        (__ \ "address").read[SubscriptionAddress] and
        (__ \ "isWelsh").readNullable[Boolean].map(_.getOrElse(false))
    )(PayeSubscriptionRequest.apply)

  given OWrites[PayeSubscriptionRequest] = Json.writes[PayeSubscriptionRequest]

  val registerWrites: Writes[PayeSubscriptionRequest] =
    given Writes[SubscriptionAddress] = SubscriptionAddress.payeRegistrationWrites
    Writes { request =>
      Json.obj(
        "agentName" -> request.agentName,
        "contactName" -> request.contactName,
        "telephoneNumber" -> request.phoneNumber,
        "emailAddress" -> request.emailAddress,
        "address" -> request.address
      )
    }

case class SaSubscriptionRequest(
  agentName: String,
  contactName: String,
  phoneNumber: Option[String],
  emailAddress: Option[String],
  address: SubscriptionAddress,
  isAbroad: Boolean,
  isWelsh: Boolean
)
extends SubscriptionRequest

object SaSubscriptionRequest:
  given Reads[SaSubscriptionRequest] =
    (
      (__ \ "agentName").read[String] and
        (__ \ "contactName").read[String] and
        (__ \ "phoneNumber").readNullable[String] and
        (__ \ "emailAddress").readNullable[String] and
        (__ \ "address").read[SubscriptionAddress] and
        (__ \ "isAbroad").read[Boolean] and
        (__ \ "isWelsh").readNullable[Boolean].map(_.getOrElse(false))
    )(SaSubscriptionRequest.apply)

  given OWrites[SaSubscriptionRequest] = Json.writes[SaSubscriptionRequest]

case class CtSubscriptionRequest(
  agentName: String,
  contactName: String,
  phoneNumber: Option[String],
  emailAddress: Option[String],
  address: SubscriptionAddress,
  isAbroad: Boolean,
  isWelsh: Boolean
)
extends SubscriptionRequest

object CtSubscriptionRequest:
  given Reads[CtSubscriptionRequest] =
    (
      (__ \ "agentName").read[String] and
        (__ \ "contactName").read[String] and
        (__ \ "phoneNumber").readNullable[String] and
        (__ \ "emailAddress").readNullable[String] and
        (__ \ "address").read[SubscriptionAddress] and
        (__ \ "isAbroad").read[Boolean] and
        (__ \ "isWelsh").readNullable[Boolean].map(_.getOrElse(false))
    )(CtSubscriptionRequest.apply)

  given OWrites[CtSubscriptionRequest] = Json.writes[CtSubscriptionRequest]