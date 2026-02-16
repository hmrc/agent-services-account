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
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector.Enrolment
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.CT
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.SA
import uk.gov.hmrc.agentservicesaccount.stubs.EnrolmentStoreProxyStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

import scala.concurrent.ExecutionContext

class EnrolmentStoreProxyConnectorISpec
extends ComponentSpecHelper
with EnrolmentStoreProxyStubs {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  lazy val connector: EnrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]

  val testGroupId = GroupId("test-group-id")

  "ES3" should {
    "return active principal enrolments on a successful 200 response" in {
      givenEs3CallSucceeds(testGroupId)(SA, CT, PAYE)

      val result = connector.queryEnrolmentsAllocatedToGroup(testGroupId).futureValue

      result shouldBe Seq(
        Enrolment(service = SA.enrolmentKey, state = "Activated"),
        Enrolment(service = CT.enrolmentKey, state = "Activated"),
        Enrolment(service = PAYE.enrolmentKey, state = "Activated")
      )
    }

    "return nothing on a successful 404 response" in {
      givenEs3CallSucceeds(testGroupId)()

      val result = connector.queryEnrolmentsAllocatedToGroup(testGroupId).futureValue

      result shouldBe Nil
    }

    "throw error when EACD returns unexpected response" in {
      givenEs3CallFails(testGroupId)

      intercept[TestFailedException](connector.queryEnrolmentsAllocatedToGroup(testGroupId).futureValue)
    }
  }

}
