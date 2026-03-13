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
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

class KnownFactsWorkItemServiceSpec
extends UnitSpec
with BeforeAndAfterEach:

  private val repository = mock[SubscriptionWorkItemRepository]
  private val service = new KnownFactsWorkItemService(repository)

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

  private val workItem = WorkItem(
    id = new ObjectId(),
    receivedAt = Instant.now(),
    updatedAt = Instant.now(),
    availableAt = Instant.now(),
    status = ProcessingStatus.InProgress,
    failureCount = 0,
    item = SubscriptionWorkItem(
      arn = Arn("TARN0000001"),
      subscriptionRequest = subscriptionRequest,
      regime = LegacyRegime.PAYE,
      agentReference = Some(AgentReference("A12345")),
      groupId = Some(GroupId("ITEM-GROUP")),
      adminCredId = Some(CredId("ITEM-ADMIN"))
    )
  )

  "KnownFactsWorkItemService" should {
    "pull outstanding items for PAYE" in {
      when(repository.pullOutstandingForRegime(
        any[LegacyRegime],
        any[Instant],
        any[Instant]
      ))
        .thenReturn(Future.successful(None))

      service.pullOutstanding(LegacyRegime.PAYE, 10.seconds).futureValue

      verify(repository).pullOutstandingForRegime(
        any[LegacyRegime],
        any[Instant],
        any[Instant]
      )
    }

    "pull outstanding items for SA" in {
      when(repository.pullOutstandingForRegime(
        any[LegacyRegime],
        any[Instant],
        any[Instant]
      ))
        .thenReturn(Future.successful(None))

      service.pullOutstanding(LegacyRegime.SA, 10.seconds).futureValue

      verify(repository).pullOutstandingForRegime(
        any[LegacyRegime],
        any[Instant],
        any[Instant]
      )
    }

    "reschedule items by marking them failed with a next run time" in {
      when(repository.markAs(
        eqTo(workItem.id),
        eqTo(ProcessingStatus.Failed),
        any[Option[Instant]]
      ))
        .thenReturn(Future.successful(true))

      service.reschedule(workItem, 10.seconds).futureValue

      verify(repository).markAs(
        eqTo(workItem.id),
        eqTo(ProcessingStatus.Failed),
        any[Option[Instant]]
      )
    }

    "complete items by marking them succeeded" in {
      when(repository.complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded)))
        .thenReturn(Future.successful(true))

      service.complete(workItem).futureValue

      verify(repository).complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded))
    }

    "mark items as requiring manual intervention" in {
      when(repository.complete(eqTo(workItem.id), eqTo(ProcessingStatus.PermanentlyFailed)))
        .thenReturn(Future.successful(true))

      service.markManualIntervention(workItem).futureValue

      verify(repository).complete(eqTo(workItem.id), eqTo(ProcessingStatus.PermanentlyFailed))
    }
  }

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(repository)
