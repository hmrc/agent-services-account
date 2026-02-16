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

package uk.gov.hmrc.agentservicesaccount.mocks

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as meq
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentservicesaccount.models.UtrChecksResponse

import scala.concurrent.Future

trait MockAgentAssuranceConnector
extends MockitoSugar { this: TestSuite =>

  val mockAgentAssuranceConnector: AgentAssuranceConnector = mock[AgentAssuranceConnector]

  def mockGetAgentUtrChecks(utr: Utr)(response: UtrChecksResponse): Unit = {
    when(mockAgentAssuranceConnector.getAgentUtrChecks(meq(utr))(using any[RequestHeader]))
      .thenReturn(Future.successful(response))
  }

}
