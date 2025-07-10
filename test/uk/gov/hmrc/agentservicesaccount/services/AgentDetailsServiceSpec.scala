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
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.helpers.TestConstants.*
import uk.gov.hmrc.agentservicesaccount.mocks.*
import uk.gov.hmrc.agentservicesaccount.models.UtrChecksResponse
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException.EntityDeceasedCheckFailed
import uk.gov.hmrc.agentservicesaccount.models.agententity.RefusalCheckException.AgentIsOnRefuseToDealList
import uk.gov.hmrc.agentservicesaccount.models.agententity.{EntityCheckException, EntityCheckResult}
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
    with MockCitizenDetailsConnector
    with MockAppConfig
    with MockEmailService
    with MockAgentAssuranceConnector
    with MockAuditService
    {

  implicit val ac: AppConfig = mockAppConfig
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val req: Request[_] = FakeRequest()
//
  val mongoLockRepository = new MongoLockRepository(mongoComponent, new CurrentTimestampSupport)
  val mongoLockService = new MongoLockService(mongoLockRepository)

  val service =
    new AgentDetailsService(
      mockDesConnector,
      mockCitizenDetailsConnector,
      mockAgentAssuranceConnector,
      mongoLockService,
      mockEmailService,
      mockAuditService
    )

  "verifyAgent" should {
    "return Some(SuspensionDetails) when the agent is suspended" in {
      
      val agentDetailsDesResponse = testAgentDetailsDesResponse
        .copy(suspensionDetails = Some(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))))

      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = true,
        businessName = None
      )

      mockGetAgentRecord(testArn)(agentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      val result = service.getAgentDetailsWithChecks(testArn).futureValue

      result shouldBe EntityCheckResult(agentDetailsDesResponse,Seq(AgentIsOnRefuseToDealList))
    }

    "return None when the agent is not suspended" in {
      val agentDetailsDesResponse = testAgentDetailsDesResponse
      
      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = false,
        isRefusalToDealWith = false,
        businessName = None
      )

      mockGetAgentRecord(testArn)(agentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      val result = service.getAgentDetailsWithChecks(testArn).futureValue

      result shouldBe EntityCheckResult(agentDetailsDesResponse,Seq.empty[EntityCheckException])
    }
    
    "return Some(SuspensionDetails) and do entityChecks and sent email with deceased failed" in {
      
      val agentDetailsDesResponse = testAgentDetailsDesResponse
        .copy(suspensionDetails = Some(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA")))),
          isAnIndividual = Some(true))

      val utrChecksResponse = UtrChecksResponse(
        isManuallyAssured = true,
        isRefusalToDealWith = false,
        businessName = None
      )

      mockGetAgentRecord(testArn)(agentDetailsDesResponse)
      mockGetAgentUtrChecks(testUtr)(utrChecksResponse)
      mockGetCitizenDeceasedFlag(SaUtr(testUtr.value))(Some(EntityDeceasedCheckFailed))
      mockSendEntityCheckNotification()
      mockAuditEntityCheckFailureNotificationSent()

      val result = service.getAgentDetailsWithChecks(testArn).futureValue

      result shouldBe EntityCheckResult(agentDetailsDesResponse, Seq(EntityDeceasedCheckFailed))
      
    }
  }

}
