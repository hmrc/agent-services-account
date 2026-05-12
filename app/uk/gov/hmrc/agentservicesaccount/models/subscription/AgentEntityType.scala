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

object AgentEntityType:

  val LimitedCompany = "Limited Company"
  val Partnership = "Partnership"
  val SoleTrader = "Sole Trader"
  val LimitedLiabilityPartnership = "Limited Liability Partnership"
  val Overseas = "Overseas"
  val Unknown = "Unknown"

  def fromOrganisationType(organisationType: Option[String]): String =
    organisationType.map(_.trim.toLowerCase) match
      case Some("partnership") => Partnership
      case Some("llp") => LimitedLiabilityPartnership
      case Some("corporate body") => LimitedCompany
      case _ => Unknown
