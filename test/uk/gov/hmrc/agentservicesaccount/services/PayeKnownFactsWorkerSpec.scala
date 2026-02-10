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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, verifyNoInteractions, when}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.PayeKnownFactsJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.{Es20Enrolment, Es20Response}
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class PayeKnownFactsWorkerSpec extends UnitSpec:

  given HeaderCarrier = HeaderCarrier()

  private val jobConfig = PayeKnownFactsJobConfig(
    initialDelay = 1.second,
    interval = 1.second,
    retryInterval = 10.seconds,
    maxAttempts = 3
  )

  private val workItemService = mock[PayeKnownFactsWorkItemService]
  private val connector = mock[EnrolmentStoreProxyConnector]
  private val worker = new PayeKnownFactsWorker(workItemService, connector, jobConfig)

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

  private def buildWorkItem(failureCount: Int) =
    WorkItem(
      id = new ObjectId(),
      receivedAt = Instant.now(),
      updatedAt = Instant.now(),
      availableAt = Instant.now(),
      status = ProcessingStatus.InProgress,
      failureCount = failureCount,
      item = SubscriptionWorkItem(
        arn = Arn("TARN0000001"),
        subscriptionRequest = subscriptionRequest,
        regime = LegacyRegime.PAYE,
        agentReference = Some(AgentReference("A12345")),
        groupId = Some("ITEM-GROUP"),
        adminCredId = Some("ITEM-ADMIN")
      )
    )

  "PayeKnownFactsWorker" should {
    "do nothing when there is no outstanding work item" in {
      when(workItemService.pullOutstanding(jobConfig.retryInterval)).thenReturn(Future.successful(None))

      worker.runOnce().futureValue

      verifyNoInteractions(connector)
    }

    "reschedule when known facts are not yet available" in {
      val workItem = buildWorkItem(failureCount = 0)
      when(workItemService.pullOutstanding(jobConfig.retryInterval)).thenReturn(Future.successful(Some(workItem)))
      when(connector.queryKnownFactsForPayeAgent(eqTo("A12345"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(workItemService.reschedule(workItem, jobConfig.retryInterval)).thenReturn(Future.successful(true))

      worker.runOnce().futureValue

      verify(workItemService).reschedule(workItem, jobConfig.retryInterval)
      verify(workItemService, never()).complete(workItem)
    }

    "allocate enrolment and complete when known facts are available" in {
      val workItem = buildWorkItem(failureCount = 0)
      val response = Es20Response("IR-PAYE-AGENT", Seq(Es20Enrolment(Nil, Nil)))

      when(workItemService.pullOutstanding(jobConfig.retryInterval)).thenReturn(Future.successful(Some(workItem)))
      when(connector.queryKnownFactsForPayeAgent(eqTo("A12345"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(response)))
      when(connector.allocatePayeAgentEnrolment(eqTo("ITEM-GROUP"), eqTo("A12345"), eqTo("ITEM-ADMIN"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(workItemService.complete(workItem)).thenReturn(Future.successful(true))

      worker.runOnce().futureValue

      verify(workItemService).complete(workItem)
      verify(workItemService, never()).reschedule(workItem, jobConfig.retryInterval)
    }

    "mark for manual intervention once max attempts are reached" in {
      val workItem = buildWorkItem(failureCount = 2)
      when(workItemService.pullOutstanding(jobConfig.retryInterval)).thenReturn(Future.successful(Some(workItem)))
      when(connector.queryKnownFactsForPayeAgent(eqTo("A12345"))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(workItemService.markManualIntervention(workItem)).thenReturn(Future.successful(true))

      worker.runOnce().futureValue

      verify(workItemService).markManualIntervention(workItem)
      verify(workItemService, never()).reschedule(workItem, jobConfig.retryInterval)
    }
  }
