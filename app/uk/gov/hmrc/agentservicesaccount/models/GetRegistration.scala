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

case class GetRegistrationRequest(
  regime: String = "ITSA",
  requiresNameMatch: Boolean = false,
  isAnAgent: Boolean = false
)

object GetRegistrationRequest:
  given OFormat[GetRegistrationRequest] = Json.format

case class GetRegistrationOrganisation(
  organisationType: Option[String]
)

object GetRegistrationOrganisation:
  given Reads[GetRegistrationOrganisation] = (__ \ "organisationType").readNullable[String].map(GetRegistrationOrganisation.apply)

case class GetRegistrationResponse(
  isAnIndividual: Boolean,
  organisation: Option[GetRegistrationOrganisation]
)

object GetRegistrationResponse:
  given Reads[GetRegistrationResponse] = Json.reads
