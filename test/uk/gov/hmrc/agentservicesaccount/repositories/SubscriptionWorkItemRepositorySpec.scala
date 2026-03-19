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
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Updates
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.SaSubscriptionRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionAddress
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.Deferred
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.InProgress
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo

import java.time.Instant
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
              agentReference = None
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

      val pulled = repository.pullOutstandingRobotics(LegacyRegime.SA, availableBefore = Instant.now()).futureValue

      pulled.map(_.id).shouldBe(Some(workItem.id))
    }

    "not re-pull a stale InProgress item once it has been invoked (avoid duplicate submissions while awaiting callback)" in {
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None
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

      val pulled = repository.pullOutstandingRobotics(LegacyRegime.SA, availableBefore = Instant.now()).futureValue

      pulled.shouldBe(None)
    }

    "not pick Deferred items for invocation retry" in {
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None
            )
          )
          .futureValue

      repository.markAs(workItem.id, Deferred).futureValue

      val pulled = repository.pullOutstandingRobotics(LegacyRegime.SA, availableBefore = Instant.now()).futureValue

      pulled.shouldBe(None)
    }

    "not overwrite a callback transition when attempting to mark an item Deferred" in {
      val workItem =
        repository
          .pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = request,
              regime = LegacyRegime.SA,
              agentReference = None
            )
          )
          .futureValue

      repository.markAs(workItem.id, InProgress).futureValue
      repository.addAgentReference(AgentReference("XS123"), requestId = workItem.item.requestId).futureValue.shouldBe(true)

      val updated = repository.markAsDeferredIfStillAwaitingInvocation(workItem.id).futureValue
      updated.shouldBe(false)

      val after = repository.findByRequestId(workItem.item.requestId).futureValue.value
      after.status.shouldBe(ToDo)
      after.item.agentReference.value.shouldBe(AgentReference("XS123"))
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
              requestId = requestId
            )
          )
          .futureValue

      repository.markAs(workItem.id, InProgress).futureValue
      repository.saveStatusToDatabase(
        workItem.id,
        InProgress,
        workItem.failureCount,
        Instant.now()
      ).futureValue.shouldBe(true)
      repository.addAgentReference(AgentReference("XS123"), requestId = requestId).futureValue.shouldBe(true)

      // Simulate the post-callback worker claiming the item.
      repository.markAs(workItem.id, InProgress).futureValue

      repository.addAgentReference(AgentReference("XS123"), requestId = requestId).futureValue.shouldBe(true)

      val after = repository.findByRequestId(requestId).futureValue.value
      after.status.shouldBe(InProgress)
      after.item.agentReference.shouldBe(Some(AgentReference("XS123")))
    }
  }
