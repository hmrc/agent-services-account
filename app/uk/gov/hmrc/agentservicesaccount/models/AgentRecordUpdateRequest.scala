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

sealed trait AgentRecordUpdateRequest

case class AmlsUpdateRequest(
  amlsDetails: AmlsDetails
)
extends AgentRecordUpdateRequest

case class AgencyDetailsUpdateRequest(
  agencyDetails: AgencyDetails
)
extends AgentRecordUpdateRequest

object AgentRecordUpdateRequest:
  given Reads[AgentRecordUpdateRequest] = Reads { json =>
    ((json \ "amlsDetails").validateOpt[AmlsDetails], (json \ "agencyDetails").validateOpt[AgencyDetails]) match {
      case (JsSuccess(Some(amlsDetails), _), JsSuccess(None, _)) => JsSuccess(AmlsUpdateRequest(amlsDetails))
      case (JsSuccess(None, _), JsSuccess(Some(agencyDetails), _)) => JsSuccess(AgencyDetailsUpdateRequest(agencyDetails))
      case (JsSuccess(None, _), JsSuccess(None, _)) => JsError("Invalid update request: neither 'amlsDetails' nor 'agencyDetails' provided")
      case (JsSuccess(Some(_), _), JsSuccess(Some(_), _)) => JsError("Invalid update request: both 'amlsDetails' and 'agencyDetails' provided")
      case _ => JsError("Invalid update request: unable to parse 'amlsDetails' or 'agencyDetails'")
    }
  }
