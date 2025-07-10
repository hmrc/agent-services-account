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
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, Utr}
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException.{CitizenConnectorRequestFailed, EntityDeceasedCheckFailed}
import uk.gov.hmrc.agentservicesaccount.models.agententity.RefusalCheckException.AgentIsOnRefuseToDealList
import uk.gov.hmrc.agentservicesaccount.models.agententity.{EmailCheckExceptions, EntityCheckException, EntityCheckResult}
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec


class EntityCheckResultSpec extends UnitSpec:

  val testAgentRecord = AgentDetailsDesResponse(
    uniqueTaxReference = Some(Utr("1234567890")),
    agencyDetails = Some(AgencyDetails(Some("Test Agency"), None, None, None)),
    suspensionDetails = Some(SuspensionDetails(true, Some(Set("ITSA")))),
    isAnIndividual = Some(false)
  )

  "EntityCheckResult" should {

    "be instantiable and equal" in {
      val checkResult = EntityCheckResult(testAgentRecord, Seq.empty)
      checkResult.agentRecord mustBe testAgentRecord
      checkResult.entityCheckExceptions mustBe Seq.empty
    }

    "hold one or more exceptions" in {
      val result = EntityCheckResult(testAgentRecord, Seq(EntityDeceasedCheckFailed, AgentIsOnRefuseToDealList))
      result.entityCheckExceptions must contain(EntityDeceasedCheckFailed)
      result.entityCheckExceptions must contain(AgentIsOnRefuseToDealList)
    }

    "support failedChecksText for EmailCheckExceptions" in {
      val emailExceptions: Seq[EmailCheckExceptions] = Seq(
        EntityDeceasedCheckFailed,
        AgentIsOnRefuseToDealList
      )

      emailExceptions.map(_.failedChecksText) must contain allOf (
        "Agent is deceased",
        "Agent is on the 'Refuse To Deal With' list"
      )
    }
  }

  "DeceasedCheckException" should {
    "include a request failure case with status code" in {
      val exception = CitizenConnectorRequestFailed(503)
      exception.code mustBe 503
    }
  }

  "EntityCheckExceptions" should {
    "serialize and deserialize EntityDeceasedCheckFailed" in {
      val json = Json.toJson(EntityDeceasedCheckFailed.toString)
      json.as[String] mustBe "EntityDeceasedCheckFailed"
    }

    "match against sealed trait for pattern matching" in {
      val ex: EntityCheckException = AgentIsOnRefuseToDealList
      val matched = ex match
        case AgentIsOnRefuseToDealList => true
        case _                         => false

      matched mustBe true
    }
  }
