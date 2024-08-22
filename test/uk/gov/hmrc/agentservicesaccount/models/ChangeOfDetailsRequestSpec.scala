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

import java.time.temporal.ChronoUnit

import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.assets.TestConstants.testChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class ChangeOfDetailsRequestSpec extends UnitSpec:

  "ChangeOfDetailsRequest" should:
    "serialize to JSON using the implicit mongoFormat" in:
      val result = Json.toJson(testChangeOfDetailsRequest)

      result shouldBe Json.obj(
        "arn" -> "AARN1234567",
        "timeSubmitted" -> Json.obj(
          "$date" -> Json.obj(
            "$numberLong" ->
              testChangeOfDetailsRequest.timeSubmitted.toEpochMilli.toString
          )
        )
      )

    "deserialize from JSON using the implicit mongoFormat" in:
      val json = Json.obj(
        "arn" -> "AARN1234567",
        "timeSubmitted" -> Json.obj(
          "$date" -> Json.obj(
            "$numberLong" ->
              testChangeOfDetailsRequest.timeSubmitted.toEpochMilli.toString
          )
        )
      )

      val result = Json.fromJson[ChangeOfDetailsRequest](json).get

      result shouldBe
        testChangeOfDetailsRequest.copy(
          timeSubmitted = testChangeOfDetailsRequest.timeSubmitted.truncatedTo(ChronoUnit.MILLIS)
        ) // Done to account for rounding errors when converting to/from JSON

    "serialize to JSON using the standard macro formatter" in:
      val result = Json.toJson(testChangeOfDetailsRequest)(ChangeOfDetailsRequest.format)

      result shouldBe Json.obj(
        "arn"           -> "AARN1234567",
        "timeSubmitted" -> testChangeOfDetailsRequest.timeSubmitted
      )

    "deserialize from JSON using the standard macro formatter" in:
      val json = Json.obj(
        "arn"           -> "AARN1234567",
        "timeSubmitted" -> testChangeOfDetailsRequest.timeSubmitted
      )

      val result = Json.fromJson[ChangeOfDetailsRequest](json)(ChangeOfDetailsRequest.format).get

      result shouldBe testChangeOfDetailsRequest
