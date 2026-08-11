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

package uk.gov.hmrc.agentservicesaccount.services

import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.scalatest.concurrent.IntegrationPatience
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.helpers.TestConstants.*
import uk.gov.hmrc.agentservicesaccount.mocks.*
import uk.gov.hmrc.agentservicesaccount.models.UtrChecksResponse
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException.EntityDeceasedCheckFailed
import uk.gov.hmrc.agentservicesaccount.models.agententity.EntityCheckException
import uk.gov.hmrc.agentservicesaccount.models.agententity.EntityCheckResult
import uk.gov.hmrc.agentservicesaccount.models.agententity.RefusalCheckException.AgentIsOnRefuseToDealList
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext

class AgentDetailsServiceSpec
extends UnitSpec
with CleanMongoCollectionSupport
with MockDesConnector
with MockHipConnector
with MockCitizenDetailsConnector
with MockAppConfig
with MockEmailService
with MockAgentAssuranceConnector
with MockAgentMappingConnector
with MockAuditService
with IntegrationPatience {

  implicit val ac: AppConfig = mockAppConfig
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val req: Request[?] = FakeRequest()

  val mongoLockRepository = new MongoLockRepository(mongoComponent, new CurrentTimestampSupport)
  val mongoLockService = new MongoLockService(mongoLockRepository)

  val service =
    new AgentDetailsService(
      ac,
      mockDesConnector,
      mockHipConnector,
      mockCitizenDetailsConnector,
      mockAgentAssuranceConnector,
      mockAgentMappingConnector,
      mongoLockService,
      mockEmailService,
      mockAuditService
    )

  val serviceHip =
    new AgentDetailsService(
      mockAppConfigHip,
      mockDesConnector,
      mockHipConnector,
      mockCitizenDetailsConnector,
      mockAgentAssuranceConnector,
      mockAgentMappingConnector,
      mongoLockService,
      mockEmailService,
      mockAuditService
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDesConnector, mockHipConnector)
  }

  "verifyAgent" should {
//    TODO: 119995 FIX THIS TEST
    "return Some(SuspensionDetails) when the agent is suspended" in {

      val agentDetailsDesResponse = testAgentDetailsDesResponse
        .copy(suspensionDetails = Some(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))))

      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = true,
        businessName = None
      )

      mockDesGetAgentRecord(testArn)(agentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      val result = service.getAgentDetailsWithChecks(testArn).futureValue

      result shouldBe EntityCheckResult(agentDetailsDesResponse, Seq(AgentIsOnRefuseToDealList))
    }

//    TODO: 119995 FIX THIS TEST
    "return None when the agent is not suspended" in {
      val agentDetailsDesResponse = testAgentDetailsDesResponse

      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = false,
        businessName = None
      )

      mockDesGetAgentRecord(testArn)(agentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      val result = service.getAgentDetailsWithChecks(testArn).futureValue

      result shouldBe EntityCheckResult(agentDetailsDesResponse, Seq.empty[EntityCheckException])
    }

//    TODO: 119995 FIX THIS TEST
    "return Some(SuspensionDetails) and do entityChecks and sent email with deceased failed" in {

      val agentDetailsDesResponse = testAgentDetailsDesResponse
        .copy(
          suspensionDetails = Some(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))),
          isAnIndividual = Some(true)
        )

      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = true,
        isRefusalToDealWith = false,
        businessName = None
      )

      mockDesGetAgentRecord(testArn)(agentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockGetCitizenDeceasedFlag(SaUtr(testUtr.value))(Some(EntityDeceasedCheckFailed))
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      val result = service.getAgentDetailsWithChecks(testArn).futureValue

      result shouldBe EntityCheckResult(agentDetailsDesResponse, Seq(EntityDeceasedCheckFailed))

    }

//    TODO: 11995 DELETE THIS TEST
//    "call DES connector when feature switch is disabled" in {
//      val utrChecksResponse = UtrChecksResponse(
//        isManuallyAssured = false,
//        isRefusalToDealWith = true,
//        businessName = None
//      )
//      mockDesGetAgentRecord(testArn)(testAgentDetailsDesResponse)
//      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
//      mockSendEntityCheckNotification()
//      mockAuditEntityCheckFailureNotificationSent()
//
//      service.getAgentDetailsWithChecks(testArn).futureValue
//
//      verify(mockDesConnector).getAgentRecord(testArn)
//      verify(mockHipConnector, never()).getAgentRecord(testArn)
//    }

//    TODO: 11995 Is this test specifically required or can it be covered by other test cases?
    "call HIP connector when feature switch is enabled" in {

      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = true,
        businessName = None
      )
      mockHipGetAgentRecord(testArn)(testAgentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      serviceHip.getAgentDetailsWithChecks(testArn).futureValue

      verify(mockHipConnector).getAgentRecord(testArn)
      verify(mockDesConnector, never()).getAgentRecord(testArn)
    }
  }

}
