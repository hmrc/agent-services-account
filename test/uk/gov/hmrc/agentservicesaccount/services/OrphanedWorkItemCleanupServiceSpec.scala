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
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.*
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.Filters.equal as mongoEq
import org.mongodb.scala.model.Updates.combine
import org.mongodb.scala.model.Updates.set
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.mocks.MockAuditService
import uk.gov.hmrc.agentservicesaccount.mocks.MockLegacySubscriptionEmailService
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class OrphanedWorkItemCleanupServiceSpec
extends UnitSpec
with IntegrationPatience
with CleanMongoCollectionSupport
with MockAuditService
with MockLegacySubscriptionEmailService
with BeforeAndAfterEach {

  given Encrypter & Decrypter = SymmetricCryptoFactory.aesCrypto("edkOOwt7uvzw1TXnFIN6aRVHkfWcgiOrbBvkEQvO65g=")

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val repoConfig = ConfigFactory.parseString(
    """work-item-repository.subscriptions.retry-in-progress-after = 1s""".stripMargin
  )

  private val repository =
    new SubscriptionWorkItemRepository(
      repoConfig,
      mongoComponent
    )

  private val appConfig = mock[AppConfig]

  private val auditService = new LegacySubscriptionAuditService(mockAuditService)

  private val service =
    new OrphanedWorkItemCleanupService(
      repository,
      auditService,
      mockLegacySubscriptionEmailService,
      appConfig
    )

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
    isAbroad = false,
    isWelsh = false
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    repository.coll.drop().toFuture().futureValue
    repository.ensureIndexes().futureValue

    reset(mockAuditService, mockLegacySubscriptionEmailService)

    mockAuditLegacySubscription()

    when(appConfig.orphanedWorkItemMaxAge)
      .thenReturn(14.days)
  }

  "cleanup" should {

    "mark eligible work items permanently failed" in {
      mockSendFailureEmailIgnoreErrors()
      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = subscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = None,
            groupId = GroupId("group-1"),
            adminCredId = CredId("cred-1")
          )
        ).futureValue

      repository.coll.updateOne(
        mongoEq("_id", workItem.id),
        set(
          "updatedAt",
          Instant.now().minusSeconds(60 * 60 * 24 * 30)
        )
      ).toFuture().futureValue

      service.cleanup().futureValue

      val persisted = repository.coll.find().first().toFuture().futureValue

      persisted.status shouldBe PermanentlyFailed
    }

    "audit each permanently failed work item" in {

      mockSendFailureEmailIgnoreErrors()
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
      repository.coll.updateOne(
        mongoEq("_id", workItem.id),
        combine(
          set(
            "updatedAt",
            Instant.now().minusSeconds(60 * 60 * 24 * 30)
          ),
          set(
            "failureCount",
            2
          )
        )
      ).toFuture().futureValue

      service.cleanup().futureValue

      verify(mockAuditService).auditLegacySubscription(
        arn = eqTo(testArn),
        regime = eqTo(LegacyRegime.SA),
        isSuccessful = eqTo(false),
        legacyAgentCode = eqTo(None),
        failureReason = argThat[Option[String]] {
          case Some(reason) =>
            reason.contains("Orphaned work-item cleanup") &&
            reason.contains("agentReferenceDefined=true") &&
            reason.contains("roboticsInvoked=true") &&
            reason.contains("retryCount=2")

          case None => false
        }
      )(using any[RequestHeader])
      verify(mockLegacySubscriptionEmailService).sendFailureEmailIgnoreErrors(any[SubscriptionWorkItem])
    }

    "skip already permanently failed work items" in {

      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = subscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = None,
            groupId = GroupId("group-1"),
            adminCredId = CredId("cred-1")
          )
        ).futureValue

      repository.markAs(workItem.id, PermanentlyFailed).futureValue

      repository.coll.updateOne(
        mongoEq("_id", workItem.id),
        set(
          "updatedAt",
          Instant.now().minusSeconds(60 * 60 * 24 * 30)
        )
      ).toFuture().futureValue

      service.cleanup().futureValue

      verify(mockAuditService, never()).auditLegacySubscription(
        any[Arn],
        any[LegacyRegime],
        any[Boolean],
        any[Option[String]],
        any[Option[String]]
      )(using any[RequestHeader])
    }

    "do nothing when no orphaned work items exist" in {
      service.cleanup().futureValue

      repository.coll.countDocuments().toFuture().futureValue shouldBe 0L
      verify(mockAuditService, never()).auditLegacySubscription(
        any[Arn],
        any[LegacyRegime],
        any[Boolean],
        any[Option[String]],
        any[Option[String]]
      )(using any[RequestHeader])
    }
  }

}
