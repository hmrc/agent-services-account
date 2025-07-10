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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.agententity.VerifyEntityRequest
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec



class VerifyEntityRequestSpec extends UnitSpec:

  val testArn = Arn("AARN1234567")
  val testRequest = VerifyEntityRequest(identifier = testArn)

  "VerifyEntityRequest" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testRequest)
      (json \ "identifier").as[String] mustBe "AARN1234567"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj("identifier" -> "AARN1234567")
      val result = Json.fromJson[VerifyEntityRequest](json).get
      result mustBe testRequest
    }

    "have correct field values" in {
      testRequest.identifier mustBe testArn
    }
  }
