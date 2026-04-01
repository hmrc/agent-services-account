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
import AmlsDetails.*

object AmlsDetails:
  opaque type SupervisoryBody = String
  object SupervisoryBody:
    def apply(value: String): SupervisoryBody = value
    given Format[SupervisoryBody] = Format(Reads.StringReads.map(apply), Writes.StringWrites)
    extension (sb: SupervisoryBody) def value: String = sb

  opaque type MembershipNumber = String
  object MembershipNumber:
    def apply(value: String): MembershipNumber = value
    given Format[MembershipNumber] = Format(Reads.StringReads.map(apply), Writes.StringWrites)
    extension (mn: MembershipNumber) def value: String = mn

  opaque type EvidenceObjectReference = String
  object EvidenceObjectReference:
    def apply(value: String): EvidenceObjectReference = value
    given Format[EvidenceObjectReference] = Format(Reads.StringReads.map(apply), Writes.StringWrites)
    extension (eor: EvidenceObjectReference) def value: String = eor

  given Format[AmlsDetails] = Json.format[AmlsDetails]

case class AmlsDetails(
  supervisoryBody: SupervisoryBody,
  membershipNumber: MembershipNumber,
  evidenceObjectReference: Option[EvidenceObjectReference] = None
)
