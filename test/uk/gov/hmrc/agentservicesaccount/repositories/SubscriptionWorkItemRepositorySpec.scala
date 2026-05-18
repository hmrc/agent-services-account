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

package uk.gov.hmrc.agentservicesaccount.repositories

import com.typesafe.config.ConfigFactory
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Updates
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.InProgress
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.PermanentlyFailed
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import scala.concurrent.ExecutionContext

class SubscriptionWorkItemRepositorySpec
extends UnitSpec
with IntegrationPatience
with CleanMongoCollectionSupport
with BeforeAndAfterEach:

  given Encrypter & Decrypter = SymmetricCryptoFactory.aesCrypto("edkOOwt7uvzw1TXnFIN6aRVHkfWcgiOrbBvkEQvO65g=")
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val repoConfig = ConfigFactory.parseString(
    """work-item-repository.subscriptions.retry-in-progress-after = 1s"""
  )
  private val repository = new SubscriptionWorkItemRepository(repoConfig, mongoComponent)

  private val testGroupId = GroupId("test-group-id")
  private val testAdminCredId = CredId("test-cred-id")
  private val testArn = Arn("AARN0000001")
  private val request = SaSubscriptionRequest(
    agentName = "Test Agency",
    contactName = "John Agent",
    phoneNumber = Some("1234567890"),
    emailAddress = Some("test@email.com"),
    address = SubscriptionAddress(
      line1 = "Line 1",
      line2 = "Line 2",
      line3 = Some("Line 3"),
      line4 = Some("Line 4"),
      postCode = Some("A11 11A")
    ),
    isAbroad = false
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    repository.coll.drop().toFuture().futureValue

  "pullOutstandingRobotics" should {
    "re-pull a stale InProgress item when it has not yet been invoked (crash recovery)" in {
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None,
              groupId = testGroupId,
              adminCredId = testAdminCredId
            )
          )
          .futureValue

      repository.markAs(workItem.id, InProgress).futureValue

      // Simulate a crash after the DB claim (InProgress) but before invocation: make the item stale and ensure there is
      // no roboticsInvokedAt marker.
      val staleUpdatedAt = Instant.now().minusSeconds(5)
      repository.coll
        .updateOne(
          Filters.equal("_id", workItem.id),
          Updates.set("updatedAt", staleUpdatedAt)
        )
        .toFuture()
        .futureValue

      val pulled =
        repository.pullAwaitingRobotics(
          LegacyRegime.SA,
          availableBefore = Instant.now(),
          failedBefore = Instant.now()
        ).futureValue

      pulled.map(_.id).shouldBe(Some(workItem.id))
    }
    "not re-pull a stale InProgress item if it was updated recently" in {
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None,
              groupId = testGroupId,
              adminCredId = testAdminCredId
            )
          )
          .futureValue

      repository.markAs(workItem.id, InProgress).futureValue

      val pulled =
        repository.pullAwaitingRobotics(
          LegacyRegime.SA,
          availableBefore = Instant.now(),
          failedBefore = Instant.now()
        ).futureValue

      pulled.map(_.id).shouldBe(None)
    }

    "not re-pull a stale InProgress item once it has been invoked (avoid duplicate submissions while awaiting callback)" in {
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None,
              groupId = testGroupId,
              adminCredId = testAdminCredId
            )
          )
          .futureValue

      repository.markAs(workItem.id, InProgress).futureValue

      val staleUpdatedAt = Instant.now().minusSeconds(5)
      repository.coll
        .updateOne(
          Filters.equal("_id", workItem.id),
          Updates.combine(
            Updates.set("updatedAt", staleUpdatedAt),
            Updates.set("item.roboticsInvokedAt", Instant.now())
          )
        )
        .toFuture()
        .futureValue

      val pulled =
        repository.pullAwaitingRobotics(
          LegacyRegime.SA,
          availableBefore = Instant.now(),
          failedBefore = Instant.now()
        ).futureValue

      pulled.shouldBe(None)
    }
  }

  "addAgentReference" should {
    "ignore duplicate success callbacks once an agentReference has been set (do not move back to ToDo)" in {
      val requestId = "dup-success-callback-request-id"
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None,
              requestId = requestId,
              groupId = testGroupId,
              adminCredId = testAdminCredId
            )
          )
          .futureValue

      repository.addAgentReference(
        AgentReference("XS1234"),
        requestId = requestId,
        LocalDateTime.of(2026, 1, 1, 1, 0).atZone(ZoneId.of("Europe/London")).toInstant
      ).futureValue.shouldBe(true)
      val afterFirstCallback = repository.findByRequestId(requestId).futureValue.value
      afterFirstCallback.status.shouldBe(ToDo)

      // Simulate the post-callback worker claiming the item.
      repository.markAs(workItem.id, InProgress).futureValue

      repository.addAgentReference(
        AgentReference("XS1234"),
        requestId = requestId,
        LocalDateTime.of(2026, 1, 1, 1, 0).atZone(ZoneId.of("Europe/London")).toInstant
      ).futureValue.shouldBe(true)

      val after = repository.findByRequestId(requestId).futureValue.value
      after.status.shouldBe(InProgress)
      after.item.agentReference.shouldBe(Some(AgentReference("XS1234")))
    }
  }

  "findOrphanedWorkItems" should {

    "return non permanently failed work items older than the cutoff" in {

      val oldWorkItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = request,
            regime = LegacyRegime.SA,
            agentReference = None,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue

      repository.markAs(oldWorkItem.id, PermanentlyFailed).futureValue

      val activeWorkItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = Arn("AARN0000002"),
            subscriptionRequest = request,
            regime = LegacyRegime.CT,
            agentReference = None,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue

      repository.coll.updateOne(
        Filters.equal("_id", activeWorkItem.id),
        Updates.set("updatedAt", Instant.now().minusSeconds(60 * 60 * 24 * 30))
      ).toFuture().futureValue

      val results =
        repository.findOrphanedWorkItems(
          Instant.now().minusSeconds(60 * 60 * 24 * 14)
        ).futureValue

      results.map(_.id) should contain(activeWorkItem.id)
      results.map(_.id) should not contain oldWorkItem.id
    }

    "not return recent work items" in {

      repository.pushNew(
        SubscriptionWorkItem(
          arn = testArn,
          subscriptionRequest = request,
          regime = LegacyRegime.SA,
          agentReference = None,
          groupId = testGroupId,
          adminCredId = testAdminCredId
        )
      ).futureValue

      val result =
        repository
          .findOrphanedWorkItems(
            Instant.now().minusSeconds(60 * 60 * 24 * 14)
          )
          .futureValue

      result shouldBe empty
    }
  }

  "markPermanentlyFailed" should {

    "mark a work item as PermanentlyFailed" in {

      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = request,
            regime = LegacyRegime.SA,
            agentReference = None,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue

      val updated = repository.markPermanentlyFailed(workItem.id).futureValue

      updated shouldBe true

      val persisted = repository.coll.find(Filters.equal("_id", workItem.id)).first().toFuture().futureValue

      persisted.status shouldBe PermanentlyFailed
    }

    "return false when already PermanentlyFailed" in {

      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = request,
            regime = LegacyRegime.SA,
            agentReference = None,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue

      repository.markAs(workItem.id, PermanentlyFailed).futureValue

      val updated = repository.markPermanentlyFailed(workItem.id).futureValue

      updated shouldBe false
    }

    "return false when work item does not exist" in {

      val result =
        repository.markPermanentlyFailed(
          new ObjectId()
        ).futureValue

      result shouldBe false
    }
  }
