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

import org.apache.pekko.Done
import org.bson.types.ObjectId
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EmailConnector
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.models.Es20Enrolment
import uk.gov.hmrc.agentservicesaccount.models.Es20Response
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.CT
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.SA
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayePostcode
import uk.gov.hmrc.agentservicesaccount.mocks.{MockAppConfig, MockLegacySubscriptionAuditService}
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

class KnownFactsWorkerSpec
extends UnitSpec
with BeforeAndAfterEach
with MockAppConfig
with MockLegacySubscriptionAuditService:

  given HeaderCarrier = HeaderCarrier()

  private val jobConfig = WorkItemJobConfig(
    enabled = true,
    schedulerDelay = 1.second,
    schedulerInterval = 1.second,
    retryInterval = 10.seconds,
    maxAttempts = 3
  )

  private val workItemService = mock[KnownFactsWorkItemService]
  private val connector = mock[EnrolmentStoreProxyConnector]
  private val emailConnector = mock[EmailConnector]

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def buildWorkItem(
    regime: LegacyRegime,
    failureCount: Int,
    agentReference: Option[AgentReference] = Some(AgentReference("A12345")),
    groupId: GroupId = GroupId("ITEM-GROUP"),
    adminCredId: CredId = CredId("ITEM-ADMIN"),
    subscriptionRequest: SubscriptionRequest
  ) = WorkItem(
    id = new ObjectId(),
    receivedAt = Instant.now(),
    updatedAt = Instant.now(),
    availableAt = Instant.now(),
    status = ProcessingStatus.InProgress,
    failureCount = failureCount,
    item = SubscriptionWorkItem(
      arn = Arn("TARN0000001"),
      subscriptionRequest = subscriptionRequest,
      regime = regime,
      agentReference = agentReference,
      groupId = groupId,
      adminCredId = adminCredId
    )
  )

  val testData: Map[LegacyRegime, SubscriptionRequest] = Map(
    PAYE -> PayeSubscriptionRequest(
      agentName = "Agent Name",
      contactName = "Contact Name",
      phoneNumber = None,
      emailAddress = None,
      address = SubscriptionAddress(
        line1 = "1 High Street",
        line2 = "Town",
        line3 = None,
        line4 = None,
        postCode = Some("AA1 1AA")
      )
    ),
    SA -> SaSubscriptionRequest(
      agentName = "Agent Name",
      contactName = "Contact Name",
      phoneNumber = None,
      emailAddress = None,
      address = SubscriptionAddress(
        line1 = "1 High Street",
        line2 = "Town",
        line3 = None,
        line4 = None,
        postCode = Some("AA1 1AA")
      ),
      isAbroad = false
    ),
    CT -> CtSubscriptionRequest(
      agentName = "Agent Name",
      contactName = "Contact Name",
      phoneNumber = None,
      emailAddress = None,
      address = SubscriptionAddress(
        line1 = "1 High Street",
        line2 = "Town",
        line3 = None,
        line4 = None,
        postCode = Some("AA1 1AA")
      ),
      isAbroad = false
    )
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(workItemService, connector, emailConnector, mockLegacySubscriptionAuditService)

  private val worker =
    new KnownFactsWorker(
      workItemService = workItemService,
      enrolmentStoreProxyConnector = connector,
      emailConnector = emailConnector,
      legacySubscriptionAuditService = mockLegacySubscriptionAuditService
    )

  testData.foreach { case (regime, subscriptionRequest) =>
    s"KnownFactsWorker for $regime" should {
      "do nothing when there is no outstanding work item" in {
        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(None))

        worker.runOnce(using jobConfig, regime).futureValue

        verifyNoInteractions(connector)
      }

      "reschedule when known facts are not yet available" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))

        when(connector.queryKnownFactsForAgent(
          eqTo(regime),
          eqTo("A12345"),
          eqTo(expectedValidatedPostcode(regime))
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        when(workItemService.markFailed(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(workItemService).markFailed(workItem)
        verify(workItemService, never()).complete(workItem)
      }

      "allocate enrolment and complete when known facts are available" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )
        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

        mockLegacySubscriptionAuditSuccess()

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))

        when(connector.queryKnownFactsForAgent(
          eqTo(regime),
          eqTo("A12345"),
          eqTo(expectedValidatedPostcode(regime))
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(response)))

        when(connector.allocateAgentEnrolment(
          any[LegacyRegime],
          any[GroupId],
          any[String],
          any[CredId]
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(()))

        when(workItemService.complete(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(workItemService).complete(workItem)
        verify(workItemService, never()).markFailed(workItem)
        val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

        verify(mockLegacySubscriptionAuditService).auditSuccess(
          arn = workItem.item.arn,
          regime = regime,
          legacyAgentCode = Some("A12345")
        )
      }

      "send a service-specific completion email after allocating the enrolment" in {
        val subscriptionRequestWithEmail =
          subscriptionRequest match
            case request: PayeSubscriptionRequest => request.copy(emailAddress = Some("agent@example.com"))
            case request: SaSubscriptionRequest => request.copy(emailAddress = Some("agent@example.com"))
            case request: CtSubscriptionRequest => request.copy(emailAddress = Some("agent@example.com"))

        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequestWithEmail
        )
        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

        mockLegacySubscriptionAuditSuccess()

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))

        when(connector.queryKnownFactsForAgent(
          eqTo(regime),
          eqTo("A12345"),
          eqTo(expectedValidatedPostcode(regime))
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(response)))

        when(connector.allocateAgentEnrolment(
          any[LegacyRegime],
          any[GroupId],
          any[String],
          any[CredId]
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(()))

        when(emailConnector.sendEmail(any[EmailInformation])(using any[play.api.mvc.RequestHeader]))
          .thenReturn(Future.successful(()))

        when(workItemService.complete(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(emailConnector).sendEmail(eqTo(EmailInformation(
          to = Seq("agent@example.com"),
          templateId = "agent_services_subscription_complete",
          parameters = Map(
            "agencyName" -> "Agent Name",
            "arn" -> "TARN0000001",
            "serviceName" -> expectedServiceName(regime),
            "serviceSectionName" -> expectedServiceSectionName(regime),
            "agentCode" -> "A12345"
          )
        )))(using any[play.api.mvc.RequestHeader])
        verify(workItemService).complete(workItem)
      }

      "complete the work item when the completion email fails after allocating the enrolment" in {
        val subscriptionRequestWithEmail =
          subscriptionRequest match
            case request: PayeSubscriptionRequest => request.copy(emailAddress = Some("agent@example.com"))
            case request: SaSubscriptionRequest => request.copy(emailAddress = Some("agent@example.com"))
            case request: CtSubscriptionRequest => request.copy(emailAddress = Some("agent@example.com"))

        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequestWithEmail
        )
        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

        mockLegacySubscriptionAuditSuccess()

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))

        when(connector.queryKnownFactsForAgent(
          eqTo(regime),
          eqTo("A12345"),
          eqTo(expectedValidatedPostcode(regime))
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(response)))

        when(connector.allocateAgentEnrolment(
          any[LegacyRegime],
          any[GroupId],
          any[String],
          any[CredId]
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(()))

        when(emailConnector.sendEmail(any[EmailInformation])(using any[play.api.mvc.RequestHeader]))
          .thenReturn(Future.failed(new RuntimeException("email service unavailable")))

        when(workItemService.complete(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(emailConnector).sendEmail(any[EmailInformation])(using any[play.api.mvc.RequestHeader])
        verify(workItemService).complete(workItem)
        verify(workItemService, never()).markFailed(workItem)
      }

      "mark for manual intervention once max attempts are reached" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 2,
          subscriptionRequest = subscriptionRequest
        )

        mockLegacySubscriptionAuditFailure()

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))

        when(connector.queryKnownFactsForAgent(
          eqTo(regime),
          eqTo("A12345"),
          eqTo(expectedValidatedPostcode(regime))
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        when(workItemService.markPermanentlyFailed(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(workItemService).markPermanentlyFailed(workItem)
        verify(workItemService, never()).markFailed(workItem)
        val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

        verify(mockLegacySubscriptionAuditService).auditFailure(
          arn = workItem.item.arn,
          regime = regime,
          failureReason = "Max retry attempts reached in KnownFactsWorker"
        )
      }
    }
  }

  "KnownFactsWorker for PAYE" should {
    "mark permanently failed without calling ES20 when postcode is blank" in {
      val workItem = buildWorkItem(
        PAYE,
        failureCount = 0,
        subscriptionRequest = testData(PAYE).asInstanceOf[PayeSubscriptionRequest].copy(
          address = testData(PAYE).address.copy(postCode = Some("   "))
        )
      )

      when(workItemService.pullOutstanding(PAYE, jobConfig.retryInterval))
        .thenReturn(Future.successful(Some(workItem)))

      when(workItemService.markPermanentlyFailed(workItem)).thenReturn(Future.successful(Done))

      worker.runOnce(using jobConfig, PAYE).futureValue

      verify(workItemService).markPermanentlyFailed(workItem)
      verifyNoInteractions(connector)
    }
  }

  private def expectedPostcode(regime: LegacyRegime): Option[String] =
    regime match {
      case PAYE => Some("AA1 1AA")
      case _ => None
    }

  private def expectedValidatedPostcode(regime: LegacyRegime): Option[PayePostcode.Valid] = PayePostcode.from(expectedPostcode(regime))

  private def expectedServiceName(regime: LegacyRegime): String =
    regime match
      case PAYE => "PAYE/CIS"
      case SA => "Self Assessment"
      case CT => "Corporation Tax"

  private def expectedServiceSectionName(regime: LegacyRegime): String =
    regime match
      case PAYE => "Pay as you earn (PAYE)/Construction Industry Scheme (CIS)"
      case SA => "Self Assessment"
      case CT => "Corporation Tax"
