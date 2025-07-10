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
import uk.gov.hmrc.agentservicesaccount.models.AgentCheckOutcome
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class AgentCheckAuditEventSpec extends UnitSpec:

  val testArn = Arn("AARN1234567")
  val testUtr = Utr("1234567890")
  val outcomes = Seq(AgentCheckOutcome("Deceased",  isSuccessful = true, None))

  val eventWithUtr = AgentCheckAuditEvent(
    agentReferenceNumber = testArn,
    utr = Some(testUtr),
    agentCheckOutcomes = outcomes
  )

  val eventWithoutUtr = AgentCheckAuditEvent(
    agentReferenceNumber = testArn,
    utr = None,
    agentCheckOutcomes = outcomes
  )

  "AgentCheckAuditEvent" should {

    "serialize to JSON with UTR" in {
      val json = Json.toJson(eventWithUtr)
      (json \ "agentReferenceNumber").as[String] mustBe "AARN1234567"
      (json \ "utr").asOpt[String] mustBe Some("1234567890")
      (json \ "agentCheckOutcomes").as[Seq[JsValue]].headOption must not be empty
    }

    "serialize to JSON without UTR" in {
      val json = Json.toJson(eventWithoutUtr)
      (json \ "agentReferenceNumber").as[String] mustBe "AARN1234567"
      (json \ "utr").asOpt[String] mustBe None
    }

    "have auditType value as 'AgentCheck'" in {
      eventWithUtr.auditType mustBe "AgentCheck"
    }
  }
