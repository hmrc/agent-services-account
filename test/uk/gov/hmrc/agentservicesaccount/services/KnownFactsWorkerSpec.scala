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

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.KnownFactsJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.Es20Enrolment
import uk.gov.hmrc.agentservicesaccount.models.Es20Response
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class KnownFactsWorkerSpec
extends UnitSpec
with BeforeAndAfterEach:

  given HeaderCarrier = HeaderCarrier()

  private val jobConfig = KnownFactsJobConfig(
    initialDelay = 1.second,
    interval = 1.second,
    retryInterval = 10.seconds,
    maxAttempts = 3
  )
  private val regime = PAYE

  private val workItemService = mock[KnownFactsWorkItemService]
  private val connector = mock[EnrolmentStoreProxyConnector]

  private val subscriptionRequest = PayeSubscriptionRequest(
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
  )

  private def buildWorkItem(
    regime: LegacyRegime,
    failureCount: Int,
    agentReference: Option[AgentReference] = Some(AgentReference("A12345")),
    groupId: Option[GroupId] = Some(GroupId("ITEM-GROUP")),
    adminCredId: Option[CredId] = Some(CredId("ITEM-ADMIN"))
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

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(workItemService, connector)

  private val worker =
    new KnownFactsWorker(
      workItemService = workItemService,
      enrolmentStoreProxyConnector = connector
    )

  "KnownFactsWorker" should {
    "do nothing when there is no outstanding work item" in {
      when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
        .thenReturn(Future.successful(None))

      worker.runOnce(using jobConfig, regime).futureValue

      verifyNoInteractions(connector)
    }

    "reschedule when known facts are not yet available" in {
      val workItem = buildWorkItem(regime, failureCount = 0)

      when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
        .thenReturn(Future.successful(Some(workItem)))

      when(connector.queryKnownFactsForAgent(eqTo(regime), eqTo("A12345"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      when(workItemService.reschedule(workItem, jobConfig.retryInterval)).thenReturn(Future.successful(true))

      worker.runOnce(using jobConfig, regime).futureValue

      verify(workItemService).reschedule(workItem, jobConfig.retryInterval)
      verify(workItemService, never()).complete(workItem)
    }

    "allocate enrolment and complete when known facts are available" in {
      val workItem = buildWorkItem(regime, failureCount = 0)
      val response = Es20Response(regime.enrolmentKey, Seq(Es20Enrolment(Nil, Nil)))

      when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
        .thenReturn(Future.successful(Some(workItem)))

      when(connector.queryKnownFactsForAgent(eqTo(regime), eqTo("A12345"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(response)))

      when(connector.allocateAgentEnrolment(
        any[LegacyRegime],
        any[GroupId],
        any[String],
        any[CredId]
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      when(workItemService.complete(workItem))
        .thenReturn(Future.successful(true))

      worker.runOnce(using jobConfig, regime).futureValue

      verify(workItemService).complete(workItem)
      verify(workItemService, never()).reschedule(workItem, jobConfig.retryInterval)
    }

    "mark for manual intervention once max attempts are reached" in {
      val workItem = buildWorkItem(regime, failureCount = 2)

      when(workItemService.pullOutstanding(regime, jobConfig.retryInterval))
        .thenReturn(Future.successful(Some(workItem)))

      when(connector.queryKnownFactsForAgent(eqTo(regime), eqTo("A12345"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      when(workItemService.markManualIntervention(workItem)).thenReturn(Future.successful(true))

      worker.runOnce(using jobConfig, regime).futureValue

      verify(workItemService).markManualIntervention(workItem)
      verify(workItemService, never()).reschedule(workItem, jobConfig.retryInterval)
    }
  }
