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

import play.api.libs.json.Json
//import uk.gov.hmrc.agentservicesaccount.assets.TestConstants.testHipAmendPayload
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

// TODO: 11584 Add unit tests for HipAmendPayload model here
class HipAmendPayloadSpec
extends UnitSpec:

  "HipAmendPayload" should:
    "true equal true" in:
      true shouldBe true
//    "serialize to JSON using the implicit mongoFormat" in:
//      val result = Json.toJson(testHipAmendPayload)
//
//      result shouldBe Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> Json.obj(
//          "$date" -> Json.obj(
//            "$numberLong" ->
//              testHipAmendPayload.timeSubmitted.toEpochMilli.toString
//          )
//        )
//      )

//    "deserialize from JSON using the implicit mongoFormat" in:
//      val json = Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> Json.obj(
//          "$date" -> Json.obj(
//            "$numberLong" ->
//              testHipAmendPayload.timeSubmitted.toEpochMilli.toString
//          )
//        )
//      )
//
//      val result = Json.fromJson[HipAmendPayload](json).get
//
//      result shouldBe
//        testHipAmendPayload.copy(
//          timeSubmitted = testHipAmendPayload.timeSubmitted.truncatedTo(ChronoUnit.MILLIS)
//        ) // Done to account for rounding errors when converting to/from JSON

//    "serialize to JSON using the standard macro formatter" in:
//      val result = Json.toJson(testHipAmendPayload)(using HipAmendPayload.format)
//
//      result shouldBe Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> testHipAmendPayload.timeSubmitted
//      )

//    "deserialize from JSON using the standard macro formatter" in:
//      val json = Json.obj(
//        "arn" -> "AARN1234567",
//        "timeSubmitted" -> testHipAmendPayload.timeSubmitted
//      )
//
//      val result = Json.fromJson[HipAmendPayload](json)(using HipAmendPayload.format).get
//
//      result shouldBe testHipAmendPayload
