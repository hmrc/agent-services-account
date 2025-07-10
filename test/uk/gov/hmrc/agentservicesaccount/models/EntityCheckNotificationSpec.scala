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
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class EntityCheckNotificationSpec extends UnitSpec:

  val testArn = Arn("AARN1234567")
  val testNotification = EntityCheckNotification(
    arn = testArn,
    utr = "1234567890",
    agencyName = "Test Agency Ltd",
    failedChecks = "Agent is deceased",
    dateTime = "2024-01-01T12:00:00Z"
  )

  "EntityCheckNotification" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testNotification)

      (json \ "arn").as[String] mustBe "AARN1234567"
      (json \ "utr").as[String] mustBe "1234567890"
      (json \ "agencyName").as[String] mustBe "Test Agency Ltd"
      (json \ "failedChecks").as[String] mustBe "Agent is deceased"
      (json \ "dateTime").as[String] mustBe "2024-01-01T12:00:00Z"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "arn" -> "AARN1234567",
        "utr" -> "1234567890",
        "agencyName" -> "Test Agency Ltd",
        "failedChecks" -> "Agent is deceased",
        "dateTime" -> "2024-01-01T12:00:00Z"
      )

      val result = Json.fromJson[EntityCheckNotification](json).get
      result mustBe testNotification
    }

    "have correct field values" in {
      testNotification.arn mustBe testArn
      testNotification.failedChecks must include ("deceased")
    }
  }
