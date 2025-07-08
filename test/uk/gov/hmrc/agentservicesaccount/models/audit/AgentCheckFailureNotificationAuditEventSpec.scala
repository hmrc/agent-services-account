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

package uk.gov.hmrc.agentservicesaccount.models.audit

import org.scalatest.matchers.must.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import java.time.LocalDateTime

class AgentCheckFailureNotificationAuditEventSpec extends AnyWordSpec:

  val testArn = Arn("AARN1234567")
  val testUtr = "1234567890"
  val testEmail = "agent@example.com"
  val testDate = LocalDateTime.of(2024, 7, 1, 14, 30)
  val testEmailData = EmailData(
    failedCheck = Seq("Deceased", "RefusedToDeal"),
    dateChecked = testDate
  )

  val testEvent = AgentCheckFailureNotificationAuditEvent(
    agentReferenceNumber = testArn,
    utr = testUtr,
    email = testEmail,
    emailData = testEmailData
  )

  "AgentCheckFailureNotificationAuditEvent" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testEvent)

      (json \ "agentReferenceNumber").as[String] mustBe "AARN1234567"
      (json \ "utr").as[String] mustBe "1234567890"
      (json \ "email").as[String] mustBe "agent@example.com"
      (json \ "emailData" \ "failedCheck").as[Seq[String]] must contain theSameElementsAs Seq("Deceased", "RefusedToDeal")
      (json \ "emailData" \ "dateChecked").as[String] mustBe "2024-07-01T14:30:00"
    }

    "have auditType value 'AgentCheckFailureNotificationSent'" in {
      testEvent.auditType mustBe "AgentCheckFailureNotificationSent"
    }
  }
