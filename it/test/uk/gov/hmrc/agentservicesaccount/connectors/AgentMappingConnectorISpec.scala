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

package uk.gov.hmrc.agentservicesaccount.connectors

import org.scalatest.exceptions.TestFailedException
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.connectors.MappingConnector.Mapping
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.stubs.AgentMappingStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

import scala.concurrent.ExecutionContext

class AgentMappingConnectorISpec
extends ComponentSpecHelper
with AgentMappingStubs {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  lazy val connector: AgentMappingConnector = app.injector.instanceOf[AgentMappingConnector]

  val testArn = Arn("TARN0000001")
  val testAgentReference = AgentReference("AB1234")
  val testAgentReference2 = AgentReference("CD5678")

  "getMappings" should {
    LegacyRegime.values.foreach { regime =>
      s"return agent reference on a successful 200 response for $regime and $testArn" in {
        givenGetMappingsCallSucceeds(testArn, regime)(testAgentReference, testAgentReference2)

        val result = connector.getMappings(testArn, regime).futureValue

        result shouldBe Seq(
          Mapping(
            arn = testArn,
            identifier = testAgentReference
          ),
          Mapping(
            arn = testArn,
            identifier = testAgentReference2
          )
        )
      }

      s"return empty list on a successful 404 response for $regime and $testArn" in {
        givenGetMappingsCallSucceeds(testArn, regime)()

        val result = connector.getMappings(testArn, regime).futureValue

        result shouldBe Nil
      }

      s"throw error when agent-mappig returns unexpected response for $regime and $testArn" in {
        givenGetMappingsCallFails(testArn, regime)

        intercept[TestFailedException](connector.getMappings(testArn, regime).futureValue)
      }
    }
  }

}
