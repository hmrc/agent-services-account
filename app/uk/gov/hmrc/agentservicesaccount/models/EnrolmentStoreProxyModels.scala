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

import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.OFormat

final case class Enrolment(
  service: String,
  state: String,
  identifiers: Seq[Identifier]
)

object Enrolment:
  given OFormat[Enrolment] = Json.format[Enrolment]

final case class EspKnownFact(
  key: String,
  value: String
)

object EspKnownFact:
  given OFormat[EspKnownFact] = Json.format[EspKnownFact]

final case class Es20Request(
  service: String,
  knownFacts: Seq[EspKnownFact]
)

object Es20Request:
  given OFormat[Es20Request] = Json.format[Es20Request]

final case class Es20Enrolment(
  identifiers: Seq[EspKnownFact],
  verifiers: Seq[EspKnownFact]
)

object Es20Enrolment:
  given OFormat[Es20Enrolment] = Json.format[Es20Enrolment]

final case class Es20Response(
  service: String,
  enrolments: Seq[Es20Enrolment]
)

object Es20Response:
  given OFormat[Es20Response] = Json.format[Es20Response]

final case class Es8Request(
  userId: String,
  `type`: String,
  action: String
)

object Es8Request:
  given Format[Es8Request] = Json.format[Es8Request]

case class Identifier(
  key: String,
  value: String
) {
  override def toString: String = s"${key.toUpperCase}~${value.replace(" ", "")}"
}

object Identifier {
  implicit val format: Format[Identifier] = Json.format[Identifier]
  implicit val ordering: Ordering[Identifier] = Ordering.by(_.key)
}
