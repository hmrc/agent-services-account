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

package uk.gov.hmrc.agentservicesaccount.models

import org.scalatest.matchers.must.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class UtrChecksResponseSpec extends UnitSpec:

  val testResponse = UtrChecksResponse(
    isManuallyAssured = true,
    isRefusalToDealWith = false,
    businessName = Some("Test Business")
  )

  "UtrChecksResponse" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testResponse)

      (json \ "isManuallyAssured").as[Boolean] mustBe true
      (json \ "isRefusalToDealWith").as[Boolean] mustBe false
      (json \ "businessName").as[String] mustBe "Test Business"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "isManuallyAssured" -> true,
        "isRefusalToDealWith" -> false,
        "businessName" -> "Test Business"
      )

      val result = Json.fromJson[UtrChecksResponse](json).get
      result mustBe testResponse
    }

    "handle missing optional fields gracefully" in {
      val json = Json.obj(
        "isManuallyAssured" -> true,
        "isRefusalToDealWith" -> true
      )

      val result = Json.fromJson[UtrChecksResponse](json).get
      result mustBe UtrChecksResponse(isManuallyAssured = true, isRefusalToDealWith = true, businessName = None)
    }
  }
