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

class DmsNotificationSpec extends AnyWordSpec:

  val testNotification = DmsNotification(
    id = "notif-123",
    status = SubmissionItemStatus.Failed,
    failureReason = Some("Invalid format")
  )

  "DmsNotification" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testNotification)

      (json \ "id").as[String] mustBe "notif-123"
      (json \ "status").as[String] mustBe "Failed"
      (json \ "failureReason").asOpt[String] mustBe Some("Invalid format")
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "id" -> "notif-123",
        "status" -> "Failed",
        "failureReason" -> "Invalid format"
      )

      val result = Json.fromJson[DmsNotification](json).get
      result mustBe testNotification
    }

    "handle missing optional failureReason during deserialization" in {
      val json = Json.obj(
        "id" -> "notif-123",
        "status" -> "Failed"
      )

      val result = Json.fromJson[DmsNotification](json).get
      result.failureReason mustBe None
    }

    "have correct field values" in {
      testNotification.id mustBe "notif-123"
      testNotification.status mustBe SubmissionItemStatus.Failed
      testNotification.failureReason mustBe Some("Invalid format")
    }
  }
