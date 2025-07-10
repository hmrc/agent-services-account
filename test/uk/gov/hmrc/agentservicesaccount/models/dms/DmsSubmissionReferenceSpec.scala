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

class DmsSubmissionReferenceSpec extends UnitSpec:

  val testRef = DmsSubmissionReference("ABC123XYZ789")

  "DmsSubmissionReference" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testRef)
      (json \ "submissionReference").as[String] mustBe "ABC123XYZ789"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj("submissionReference" -> "ABC123XYZ789")
      val result = Json.fromJson[DmsSubmissionReference](json).get
      result mustBe testRef
    }

    "create a new submission reference of correct format" in {
      val generated = DmsSubmissionReference.create
      generated.submissionReference.length mustBe 12
      generated.submissionReference.forall(c => c.isDigit || (c.isLetter && c.isUpper)) mustBe true
    }
    
  }
