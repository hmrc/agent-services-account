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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.Instant
import scala.concurrent.ExecutionContext

class RoboticsWorkItemServiceSpec
extends UnitSpec
with CleanMongoCollectionSupport
with BeforeAndAfterEach {

  given Encrypter & Decrypter = SymmetricCryptoFactory.aesCrypto("edkOOwt7uvzw1TXnFIN6aRVHkfWcgiOrbBvkEQvO65g=")

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val repoConfig = ConfigFactory.parseString(
    """work-item-repository.subscriptions.retry-in-progress-after = "1s""""
  )

  private val repository = new SubscriptionWorkItemRepository(repoConfig, mongoComponent)

  private val appConfig = mock[AppConfig]

  private val service = new RoboticsWorkItemService(repository)

  private val testArn = Arn("AARN0000001")

  private val subscriptionRequest = SaSubscriptionRequest(
    agentName = "Test Agency",
    contactName = "John Agent",
    phoneNumber = Some("1234567890"),
    emailAddress = Some("test@test.com"),
    address = SubscriptionAddress(
      line1 = "Line 1",
      line2 = "Line 2",
      line3 = None,
      line4 = None,
      postCode = Some("AA1 1AA")
    ),
    isAbroad = false
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    repository.coll.drop().toFuture().futureValue

  }

  "markFailed" should {

    "update repository and set status to Failed" in {

      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = subscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = Some(AgentReference("ABC1234")),
            roboticsInvokedAt = Some(Instant.now()),
            groupId = GroupId("group-1"),
            adminCredId = CredId("cred-1")
          )
        ).futureValue

      service.markFailed(workItem).futureValue shouldBe Done

      val updated = repository.coll.find().first().toFuture().futureValue

      updated.status shouldBe ProcessingStatus.Failed
    }
  }

  "markAsInvoked" should {

    "mark robotics invoked timestamp" in {

      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = subscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = Some(AgentReference("ABC1234")),
            roboticsInvokedAt = Some(Instant.now()),
            groupId = GroupId("group-1"),
            adminCredId = CredId("cred-1")
          )
        ).futureValue

      service.markAsInvoked(workItem).futureValue shouldBe Done

      val updated = repository.coll.find().first().toFuture().futureValue

      updated.item.roboticsInvokedAt should not be empty
    }
  }

  "markPermanentlyFailed" should {

    "set status to PermanentlyFailed" in {

      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = subscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = Some(AgentReference("ABC1234")),
            roboticsInvokedAt = Some(Instant.now()),
            groupId = GroupId("group-1"),
            adminCredId = CredId("cred-1")
          )
        ).futureValue

      repository.markAs(workItem.id, ProcessingStatus.InProgress).futureValue

      service.markPermanentlyFailed(workItem).futureValue shouldBe Done

      val updated = repository.coll.find().first().toFuture().futureValue

      updated.status shouldBe ProcessingStatus.PermanentlyFailed
    }
  }

}
