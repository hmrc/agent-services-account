/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.DesConnector
import uk.gov.hmrc.agentservicesaccount.connectors.HipConnector
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.models.DesRegistrationOrganisation
import uk.gov.hmrc.agentservicesaccount.models.DesRegistrationResponse
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentEntityType
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentEntityTypeServiceSpec
extends UnitSpec
with BeforeAndAfterEach:

  private val appConfig = mock[AppConfig]
  private val desConnector = mock[DesConnector]
  private val hipConnector = mock[HipConnector]
  private val service =
    new AgentEntityTypeService(
      appConfig,
      desConnector,
      hipConnector
    )

  private val testArn = Arn("AARN0000001")
  private val testUtr = Utr("7000000002")
  private val agentRecordWithUtr = AgentDetailsDesResponse(
    uniqueTaxReference = Some(testUtr),
    agencyDetails = None,
    suspensionDetails = None,
    isAnIndividual = None
  )
  private val agentRecordWithoutUtr = agentRecordWithUtr.copy(uniqueTaxReference = None)
  private given RequestHeader = FakeRequest()

  "resolve" should {
    "use HIP for agent record lookup when the feature flag is enabled" in {
      when(appConfig.getAgentRecordViaHIP).thenReturn(true)
      when(hipConnector.getAgentRecord(eqTo(testArn))(using any[RequestHeader])).thenReturn(Future.successful(agentRecordWithUtr))
      when(desConnector.getRegistration(eqTo(testUtr))(using any[RequestHeader]))
        .thenReturn(Future.successful(Some(DesRegistrationResponse(
          isAnIndividual = true,
          organisation = None
        ))))

      service.resolve(testArn).futureValue shouldBe AgentEntityType.SoleTrader

      verify(hipConnector).getAgentRecord(eqTo(testArn))(using any[RequestHeader])
      verify(desConnector, never()).getAgentRecord(eqTo(testArn))(using any[RequestHeader])
    }

    "use DES for agent record lookup when the feature flag is disabled" in {
      when(appConfig.getAgentRecordViaHIP).thenReturn(false)
      when(desConnector.getAgentRecord(eqTo(testArn))(using any[RequestHeader])).thenReturn(Future.successful(agentRecordWithUtr))
      when(desConnector.getRegistration(eqTo(testUtr))(using any[RequestHeader]))
        .thenReturn(Future.successful(Some(DesRegistrationResponse(
          isAnIndividual = false,
          organisation = Some(DesRegistrationOrganisation(Some("Partnership")))
        ))))

      service.resolve(testArn).futureValue shouldBe AgentEntityType.Partnership

      verify(desConnector).getAgentRecord(eqTo(testArn))(using any[RequestHeader])
      verify(hipConnector, never()).getAgentRecord(eqTo(testArn))(using any[RequestHeader])
    }

    "return Overseas when the agent record has no UTR" in {
      when(appConfig.getAgentRecordViaHIP).thenReturn(true)
      when(hipConnector.getAgentRecord(eqTo(testArn))(using any[RequestHeader])).thenReturn(Future.successful(agentRecordWithoutUtr))

      service.resolve(testArn).futureValue shouldBe AgentEntityType.Overseas

      verify(desConnector, never()).getRegistration(any[Utr])(using any[RequestHeader])
    }

    Seq(
      "LLP" -> AgentEntityType.LimitedLiabilityPartnership,
      "Corporate body" -> AgentEntityType.LimitedCompany,
      "Corporate Body" -> AgentEntityType.LimitedCompany,
      "Not Specified" -> AgentEntityType.Unknown,
      "Unincorporated body" -> AgentEntityType.Unknown,
      "0000" -> AgentEntityType.Unknown
    ).foreach { case (desOrganisationType, expectedEntityType) =>
      s"map DES organisation type '$desOrganisationType' to '$expectedEntityType'" in {
        when(appConfig.getAgentRecordViaHIP).thenReturn(true)
        when(hipConnector.getAgentRecord(eqTo(testArn))(using any[RequestHeader])).thenReturn(Future.successful(agentRecordWithUtr))
        when(desConnector.getRegistration(eqTo(testUtr))(using any[RequestHeader]))
          .thenReturn(Future.successful(Some(DesRegistrationResponse(
            isAnIndividual = false,
            organisation = Some(DesRegistrationOrganisation(Some(desOrganisationType)))
          ))))

        service.resolve(testArn).futureValue shouldBe expectedEntityType
      }
    }

    "return Unknown when registration lookup returns no data" in {
      when(appConfig.getAgentRecordViaHIP).thenReturn(true)
      when(hipConnector.getAgentRecord(eqTo(testArn))(using any[RequestHeader])).thenReturn(Future.successful(agentRecordWithUtr))
      when(desConnector.getRegistration(eqTo(testUtr))(using any[RequestHeader])).thenReturn(Future.successful(None))

      service.resolve(testArn).futureValue shouldBe AgentEntityType.Unknown
    }

    "return Unknown when a lookup fails" in {
      when(appConfig.getAgentRecordViaHIP).thenReturn(true)
      when(hipConnector.getAgentRecord(eqTo(testArn))(using any[RequestHeader])).thenReturn(Future.failed(new RuntimeException("boom")))

      service.resolve(testArn).futureValue shouldBe AgentEntityType.Unknown
    }
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(
      appConfig,
      desConnector,
      hipConnector
    )
