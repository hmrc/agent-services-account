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
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.Enrolment
import uk.gov.hmrc.agentservicesaccount.models.Es20Enrolment
import uk.gov.hmrc.agentservicesaccount.models.Es20Response
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.Identifier
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.CT
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.SA
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayePostcode
import uk.gov.hmrc.agentservicesaccount.mocks.MockAppConfig
import uk.gov.hmrc.agentservicesaccount.mocks.MockLegacySubscriptionAuditService
import uk.gov.hmrc.agentservicesaccount.mocks.MockLegacySubscriptionEmailService
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

class KnownFactsWorkerSpec
extends UnitSpec
with BeforeAndAfterEach
with MockAppConfig
with MockLegacySubscriptionAuditService
with MockLegacySubscriptionEmailService:

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
  private val usersGroupsSearchConnector = mock[UsersGroupsSearchConnector]

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
      ),
      isWelsh = false
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
      isAbroad = false,
      isWelsh = false
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
      isAbroad = false,
      isWelsh = false
    )
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(
      workItemService,
      connector,
      usersGroupsSearchConnector,
      mockLegacySubscriptionAuditService,
      mockLegacySubscriptionEmailService
    )

  private val worker =
    new KnownFactsWorker(
      workItemService = workItemService,
      enrolmentStoreProxyConnector = connector,
      usersGroupsSearchConnector = usersGroupsSearchConnector,
      legacySubscriptionAuditService = mockLegacySubscriptionAuditService,
      legacySubscriptionEmailService = mockLegacySubscriptionEmailService
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
        mockSendCompletionEmailIgnoreErrors()

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

      "recover from INVALID_CREDENTIAL_ID by allocating with another group admin" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )
        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))
        val replacementAdminCredId = CredId("REPLACEMENT-ADMIN")

        mockLegacySubscriptionAuditSuccess()
        mockSendCompletionEmailIgnoreErrors()

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
          .thenReturn(
            Future.failed(invalidCredentialIdError),
            Future.successful(())
          )

        when(usersGroupsSearchConnector.getFirstAdminCredId(any[GroupId])(using any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(replacementAdminCredId)))

        when(workItemService.complete(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(usersGroupsSearchConnector).getFirstAdminCredId(any[GroupId])(using any[HeaderCarrier])
        verify(connector, times(2)).allocateAgentEnrolment(
          any[LegacyRegime],
          any[GroupId],
          any[String],
          any[CredId]
        )(using any[HeaderCarrier])
        verify(workItemService).complete(workItem)
        verify(workItemService, never()).markFailed(workItem)
      }

      "mark failed when INVALID_CREDENTIAL_ID is returned and no other group admin is available" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )
        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

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
          .thenReturn(Future.failed(multipleErrorsWithInvalidCredentialId))

        when(usersGroupsSearchConnector.getFirstAdminCredId(any[GroupId])(using any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        when(workItemService.markFailed(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(usersGroupsSearchConnector).getFirstAdminCredId(any[GroupId])(using any[HeaderCarrier])
        verify(workItemService).markFailed(workItem)
        verify(workItemService, never()).complete(workItem)
      }

      "not recover from other ES8 errors" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )
        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

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
          .thenReturn(Future.failed(UpstreamErrorResponse(
            """{"code":"OTHER_ERROR","message":"No"}""",
            400,
            400
          )))

        when(workItemService.markFailed(workItem)).thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verifyNoInteractions(usersGroupsSearchConnector)
        verify(workItemService).markFailed(workItem)
        verify(workItemService, never()).complete(workItem)
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
        mockSendCompletionEmailIgnoreErrors()

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

        verify(mockLegacySubscriptionEmailService).sendCompletionEmailIgnoreErrors(workItem.item)
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
        mockSendCompletionEmailIgnoreErrors()

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

        verify(mockLegacySubscriptionEmailService).sendCompletionEmailIgnoreErrors(workItem.item)
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
        mockSendFailureEmailIgnoreErrors()

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
        verify(mockLegacySubscriptionEmailService).sendFailureEmailIgnoreErrors(workItem.item)
      }

      "recover from MULTIPLE_ENROLMENTS_INVALID by deallocating and retrying ES8" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )

        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))
        mockLegacySubscriptionAuditSuccess()
        mockSendCompletionEmailIgnoreErrors()

        when(connector.queryKnownFactsForAgent(
          eqTo(regime),
          eqTo("A12345"),
          eqTo(expectedValidatedPostcode(regime))
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(response)))

        // ES8 first attempt fails with MULTIPLE_ENROLMENTS_INVALID
        when(connector.allocateAgentEnrolment(
          any[LegacyRegime],
          any[GroupId],
          any[String],
          any[CredId]
        )(using any[HeaderCarrier]))
          .thenReturn(
            Future.failed(multipleEnrolmentsInvalidError), // 1st call
            Future.successful(()) // 2nd call
          )

        when(connector.queryEnrolmentsAllocatedToGroup(any[GroupId])(using any[RequestHeader]))
          .thenReturn(Future.successful(
            Seq(Enrolment(
              service = regime.enrolmentKey,
              state = "Inactive",
              identifiers = Seq(Identifier("IRAgentReference", "OLD-AGENT-REF"))
            ))
          ))

        when(connector.deallocateAgentEnrolment(
          any[GroupId],
          any[LegacyRegime],
          any[String]
        )(using any[HeaderCarrier]))
          .thenReturn(Future.successful(()))

        when(workItemService.complete(workItem))
          .thenReturn(Future.successful(Done))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(connector).deallocateAgentEnrolment(
          any[GroupId],
          any[LegacyRegime],
          any[String]
        )(using any[HeaderCarrier])

        verify(workItemService).complete(workItem)
        verify(workItemService, never()).markFailed(workItem)
      }

      "fail permanently when MULTIPLE_ENROLMENTS_INVALID but enrolment is already ACTIVE" in {
        val workItem = buildWorkItem(
          regime,
          failureCount = 0,
          subscriptionRequest = subscriptionRequest
        )

        val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

        when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
          .thenReturn(Future.successful(Some(workItem)))
        mockLegacySubscriptionAuditFailure()
        when(workItemService.markPermanentlyFailed(any[WorkItem[SubscriptionWorkItem]]))
          .thenReturn(Future.successful(Done))

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
          .thenReturn(Future.failed(multipleEnrolmentsInvalidError))

        when(connector.queryEnrolmentsAllocatedToGroup(any[GroupId])(using any[RequestHeader]))
          .thenReturn(Future.successful(
            Seq(Enrolment(
              service = regime.enrolmentKey,
              state = "Activated",
              identifiers = Seq.empty
            ))
          ))

        worker.runOnce(using jobConfig, regime).futureValue

        verify(mockLegacySubscriptionAuditService).auditFailure(
          arn = workItem.item.arn,
          regime = regime,
          failureReason = s"Agent already subscribed to $regime"
        )
        verify(workItemService).markPermanentlyFailed(workItem)
        verify(workItemService, never()).complete(workItem)
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

  private def invalidCredentialIdError: UpstreamErrorResponse = UpstreamErrorResponse(
    """{"code":"INVALID_CREDENTIAL_ID","message":"Credential id is invalid"}""",
    400,
    400
  )

  private def multipleErrorsWithInvalidCredentialId: UpstreamErrorResponse = UpstreamErrorResponse(
    """{"code":"MULTIPLE_ERRORS","message":"Multiple errors have occurred","errors":[{"code":"INVALID_CREDENTIAL_ID","message":"Credential id is invalid"}]}""",
    400,
    400
  )

  private def multipleEnrolmentsInvalidError: UpstreamErrorResponse = UpstreamErrorResponse(
    """{"code":"MULTIPLE_ENROLMENTS_INVALID"}""",
    409,
    409
  )
