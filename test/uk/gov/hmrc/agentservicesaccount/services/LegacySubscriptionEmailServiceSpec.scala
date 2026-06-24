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
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.connectors.EmailConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LegacySubscriptionEmailServiceSpec
extends UnitSpec
with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val emailConnector = mock[EmailConnector]

  private val service = new LegacySubscriptionEmailService(emailConnector)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(emailConnector)
  }

  private def buildWorkItem(
    regime: LegacyRegime,
    isWelsh: Boolean = false,
    email: Option[String] = Some("agent@example.com"),
    agentReference: Option[AgentReference] = Some(AgentReference("A12345"))
  ): SubscriptionWorkItem = SubscriptionWorkItem(
    arn = Arn("TARN0000001"),
    subscriptionRequest =
      regime match {
        case PAYE =>
          PayeSubscriptionRequest(
            agentName = "Agent Name",
            contactName = "Contact Name",
            phoneNumber = None,
            emailAddress = email,
            address = SubscriptionAddress(
              "1 High Street",
              "Town",
              None,
              None,
              Some("AA1 1AA")
            ),
            isWelsh = isWelsh
          )

        case SA =>
          SaSubscriptionRequest(
            agentName = "Agent Name",
            contactName = "Contact Name",
            phoneNumber = None,
            emailAddress = email,
            address = SubscriptionAddress(
              "1 High Street",
              "Town",
              None,
              None,
              Some("AA1 1AA")
            ),
            isAbroad = false,
            isWelsh = isWelsh
          )

        case CT =>
          CtSubscriptionRequest(
            agentName = "Agent Name",
            contactName = "Contact Name",
            phoneNumber = None,
            emailAddress = email,
            address = SubscriptionAddress(
              "1 High Street",
              "Town",
              None,
              None,
              Some("AA1 1AA")
            ),
            isAbroad = false,
            isWelsh = isWelsh
          )
      },
    regime = regime,
    agentReference = agentReference,
    groupId = GroupId("group"),
    adminCredId = CredId("cred")
  )

  private def expectedServiceName(regime: LegacyRegime): String =
    regime match {
      case PAYE => "PAYE/CIS"
      case SA => "Self Assessment"
      case CT => "Corporation Tax"
    }

  private def expectedServiceSectionName(regime: LegacyRegime): String =
    regime match {
      case PAYE => "Pay as you earn (PAYE)/Construction Industry Scheme (CIS)"
      case SA => "Self Assessment"
      case CT => "Corporation Tax"
    }

  private def expectedWelshServiceName(regime: LegacyRegime): String =
    regime match
      case PAYE => "TWE/CIS"
      case SA => "Hunanasesiad"
      case CT => "Treth Gorfforaeth"

  private def expectedWelshServiceSectionName(regime: LegacyRegime): String =
    regime match
      case PAYE => "Talu wrth ennill (TWE)/Cynllun y Diwydiant Adeiladu (CIS)"
      case SA => "Hunanasesiad"
      case CT => "Treth Gorfforaeth"

  List(PAYE, SA, CT).foreach { regime =>

    s"sendCompletionEmailIgnoreErrors for $regime" should {

      "send the correct completion email" in {
        val workItem = buildWorkItem(regime)

        when(
          emailConnector.sendEmail(any[EmailInformation])(using any[RequestHeader])
        ).thenReturn(Future.successful(()))

        service.sendCompletionEmailIgnoreErrors(workItem).futureValue

        verify(emailConnector).sendEmail(
          eqTo(
            EmailInformation(
              to = Seq("agent@example.com"),
              templateId = "agent_services_subscription_complete",
              parameters = Map(
                "agencyName" -> "Agent Name",
                "arn" -> "TARN0000001",
                "serviceName" -> expectedServiceName(regime),
                "serviceSectionName" -> expectedServiceSectionName(regime),
                "agentCode" -> "A12345"
              )
            )
          )
        )(using any[RequestHeader])
      }

      "send the correct Welsh completion email" in {
        val workItem = buildWorkItem(regime, isWelsh = true)

        when(
          emailConnector.sendEmail(any[EmailInformation])(using any[RequestHeader])
        ).thenReturn(Future.successful(()))

        service.sendCompletionEmailIgnoreErrors(workItem).futureValue

        verify(emailConnector).sendEmail(
          eqTo(
            EmailInformation(
              to = Seq("agent@example.com"),
              templateId = "agent_services_subscription_complete_cy",
              parameters = Map(
                "agencyName" -> "Agent Name",
                "arn" -> "TARN0000001",
                "serviceName" -> expectedWelshServiceName(regime),
                "serviceSectionName" -> expectedWelshServiceSectionName(regime),
                "agentCode" -> "A12345"
              )
            )
          )
        )(using any[RequestHeader])
      }

      "do nothing when email address is missing" in {
        val workItem = buildWorkItem(regime, email = None)

        service.sendCompletionEmailIgnoreErrors(workItem).futureValue

        verifyNoInteractions(emailConnector)
      }

      "do nothing when agent reference is missing" in {
        val workItem = buildWorkItem(regime, agentReference = None)

        service.sendCompletionEmailIgnoreErrors(workItem).futureValue

        verifyNoInteractions(emailConnector)
      }

      "swallow connector exceptions" in {
        val workItem = buildWorkItem(regime)

        when(
          emailConnector.sendEmail(any[EmailInformation])(using any[RequestHeader])
        ).thenReturn(
          Future.failed(new RuntimeException("email service unavailable"))
        )

        service.sendCompletionEmailIgnoreErrors(workItem).futureValue

        verify(emailConnector).sendEmail(
          any[EmailInformation]
        )(using any[RequestHeader])
      }
    }

    s"sendFailureEmailIgnoreErrors for $regime" should {

      "send the correct failure email" in {
        val workItem = buildWorkItem(regime)

        when(
          emailConnector.sendEmail(any[EmailInformation])(using any[RequestHeader])
        ).thenReturn(Future.successful(()))

        service.sendFailureEmailIgnoreErrors(workItem).futureValue

        verify(emailConnector).sendEmail(
          eqTo(
            EmailInformation(
              to = Seq("agent@example.com"),
              templateId = "agent_services_subscription_fail",
              parameters = Map(
                "agencyName" -> "Agent Name",
                "serviceName" -> expectedServiceName(regime)
              )
            )
          )
        )(using any[RequestHeader])
      }

      "send the correct Welsh failure email" in {
        val workItem = buildWorkItem(regime, isWelsh = true)

        when(
          emailConnector.sendEmail(any[EmailInformation])(using any[RequestHeader])
        ).thenReturn(Future.successful(()))

        service.sendFailureEmailIgnoreErrors(workItem).futureValue

        verify(emailConnector).sendEmail(
          eqTo(
            EmailInformation(
              to = Seq("agent@example.com"),
              templateId = "agent_services_subscription_fail_cy",
              parameters = Map(
                "agencyName" -> "Agent Name",
                "serviceName" -> expectedWelshServiceName(regime)
              )
            )
          )
        )(using any[RequestHeader])
      }

      "do nothing when email address is missing" in {
        val workItem = buildWorkItem(regime, email = None)

        service.sendFailureEmailIgnoreErrors(workItem).futureValue

        verifyNoInteractions(emailConnector)
      }

      "swallow connector exceptions" in {
        val workItem = buildWorkItem(regime)

        when(
          emailConnector.sendEmail(any[EmailInformation])(using any[RequestHeader])
        ).thenReturn(
          Future.failed(new RuntimeException("email service unavailable"))
        )

        service.sendFailureEmailIgnoreErrors(workItem).futureValue

        verify(emailConnector).sendEmail(
          any[EmailInformation]
        )(using any[RequestHeader])
      }
    }
  }

}
