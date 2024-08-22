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

import java.time.Instant

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

case class ChangeOfDetailsRequest(arn: String, timeSubmitted: Instant)

object ChangeOfDetailsRequest:
  given mongoFormat: OFormat[ChangeOfDetailsRequest] =
    (__ \ "arn")
      .format[String]
      .and((__ \ "timeSubmitted").format[Instant](MongoJavatimeFormats.instantFormat))(
        ChangeOfDetailsRequest.apply,
        changeOfDetailsRequest => (changeOfDetailsRequest.arn, changeOfDetailsRequest.timeSubmitted)
      )

  val format: OFormat[ChangeOfDetailsRequest] = Json.format[ChangeOfDetailsRequest]
