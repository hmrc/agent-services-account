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

package uk.gov.hmrc.agentservicesaccount.models.agententity

import org.scalatest.matchers.must.Matchers.*
import org.scalatest.wordspec.AnyWordSpec

class EntityCheckExceptionSpec extends AnyWordSpec:

  "EntityCheckException" should {

    "return correct failedChecksText for EntityDeceasedCheckFailed" in {
      val exception = DeceasedCheckException.EntityDeceasedCheckFailed
      exception.failedChecksText mustBe "Agent is deceased"
    }

    "return correct failedChecksText for AgentIsOnRefuseToDealList" in {
      val exception = RefusalCheckException.AgentIsOnRefuseToDealList
      exception.failedChecksText mustBe "Agent is on the 'Refuse To Deal With' list"
    }

    "correctly store and return code in CitizenConnectorRequestFailed" in {
      val exception = DeceasedCheckException.CitizenConnectorRequestFailed(418)
      exception.code mustBe 418
    }

    "be assignable to EntityCheckException for all subtypes" in {
      val deceased: EntityCheckException = DeceasedCheckException.EntityDeceasedCheckFailed
      val refusal: EntityCheckException = RefusalCheckException.AgentIsOnRefuseToDealList
      val failure: EntityCheckException = DeceasedCheckException.CitizenConnectorRequestFailed(500)

      deceased mustBe DeceasedCheckException.EntityDeceasedCheckFailed
      refusal mustBe RefusalCheckException.AgentIsOnRefuseToDealList
      failure mustBe DeceasedCheckException.CitizenConnectorRequestFailed(500)
    }
  }
