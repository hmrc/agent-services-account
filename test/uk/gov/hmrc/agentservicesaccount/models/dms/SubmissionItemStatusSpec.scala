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

class SubmissionItemStatusSpec
extends UnitSpec:

  "SubmissionItemStatus" should {
    "serialize to JSON correctly" in {
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Completed).as[String] mustBe "Completed"
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Failed).as[String] mustBe "Failed"
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Forwarded).as[String] mustBe "Forwarded"
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Processed).as[String] mustBe "Processed"
      Json.toJson[SubmissionItemStatus](SubmissionItemStatus.Submitted).as[String] mustBe "Submitted"
    }

    "deserialize from JSON correctly" in {
      Json.fromJson[SubmissionItemStatus](JsString("Completed")).get mustBe SubmissionItemStatus.Completed
      Json.fromJson[SubmissionItemStatus](JsString("Failed")).get mustBe SubmissionItemStatus.Failed
      Json.fromJson[SubmissionItemStatus](JsString("Forwarded")).get mustBe SubmissionItemStatus.Forwarded
      Json.fromJson[SubmissionItemStatus](JsString("Processed")).get mustBe SubmissionItemStatus.Processed
      Json.fromJson[SubmissionItemStatus](JsString("Submitted")).get mustBe SubmissionItemStatus.Submitted
    }

    "fail to deserialize invalid JSON values" in {
      val result = Json.fromJson[SubmissionItemStatus](JsString("UnknownStatus"))
      result.isError mustBe true
    }

    "contain all expected values" in {
      SubmissionItemStatus.values must contain theSameElementsAs Seq(
        SubmissionItemStatus.Completed,
        SubmissionItemStatus.Failed,
        SubmissionItemStatus.Forwarded,
        SubmissionItemStatus.Processed,
        SubmissionItemStatus.Submitted
      )
    }
  }
