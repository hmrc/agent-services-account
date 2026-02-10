/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.models.dms

import org.scalatest.matchers.must.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

import java.time.Instant

class DmsResponseSpec
extends UnitSpec:

  val processingDate = Instant.parse("2024-07-01T10:15:30Z")
  val reference = "ABC1234567890"
  val testResponse = DmsResponse(
    processingDate = processingDate,
    reference = reference
  )

  "DmsResponse" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testResponse)

      (json \ "processingDate").as[Instant] mustBe processingDate
      (json \ "reference").as[String] mustBe reference
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "processingDate" -> "2024-07-01T10:15:30Z",
        "reference" -> "ABC1234567890"
      )

      val result = Json.fromJson[DmsResponse](json).get
      result mustBe testResponse
    }

    "have correct field values" in {
      testResponse.processingDate mustBe processingDate
      testResponse.reference mustBe reference
    }
  }
