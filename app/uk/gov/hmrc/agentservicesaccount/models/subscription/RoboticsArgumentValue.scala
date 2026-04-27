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

import play.api.libs.json.{Json, Writes}

case class RoboticsArgumentValue(
                                                requestId: String,
                                                targetSystem: String,
                                                operationRequired: String,
                                                entityType: String,
                                                agentName: String,
                                                tradingAs: String,
                                                isAbroad: Boolean,
                                                addressLine1: String,
                                                addressLine2: String,
                                                addressLine3: Option[String] = None,
                                                addressLine4: Option[String] = None,
                                                postcode: Option[String] = None,
                                                phone: Option[String] = None,
                                                ARN: String
                                              )

object RoboticsArgumentValue {
  
  implicit val roboticsArgumentValueWrites: Writes[RoboticsArgumentValue] =
    Writes {
      value =>
        Json.obj(
          "requestId" -> value.requestId,
          "targetSystem" -> value.targetSystem,
          "operationRequired" -> value.operationRequired,
          "entityType" -> value.entityType,
          "agentName" -> value.agentName,
          "tradingAs" -> value.tradingAs,
          "isAbroad" -> value.isAbroad,
          "addressLine1" -> value.addressLine1,
          "addressLine2" -> value.addressLine2,
          "addressLine3" -> value.addressLine3,
          "addressLine4" -> value.addressLine4,
          "postcode" -> value.postcode,
          "phone" -> value.phone,
          "ARN" -> value.ARN,
        )
    }

}
