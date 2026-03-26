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
import org.mockito.Mockito.when
import org.mongodb.scala.ObservableFuture
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.mongodb.scala.SingleObservableFuture
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.AgentMappingConnector
import uk.gov.hmrc.agentservicesaccount.connectors.AgentEpayeRegistrationConnector
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.Enrolment
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.CT
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.SA
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.InProgress
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.PermanentlyFailed
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionServiceSpec
extends UnitSpec
with IntegrationPatience
with CleanMongoCollectionSupport
with BeforeAndAfterEach {

  private val testArn = Arn("AARN0000001")
  private val testGroupId = GroupId("test-group-id")
  private val testAdminCredId = CredId("test-cred-id")
  private val testAgentRef = AgentReference("AB1234")
  private val subscriptionRequest = PayeSubscriptionRequest(
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
    )
  )

  private val saSubscriptionRequest = SaSubscriptionRequest(
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

  private val ctSubscriptionRequest = CtSubscriptionRequest(
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

  private val testRequest: RequestHeader = FakeRequest()
    .withHeaders(
      "Authorization" -> "Bearer test-token",
      "X-Session-ID" -> "session-123"
    )

  given Encrypter & Decrypter = SymmetricCryptoFactory.aesCrypto("edkOOwt7uvzw1TXnFIN6aRVHkfWcgiOrbBvkEQvO65g=")
  private val repoConfig = ConfigFactory.parseString(
    """work-item-repository.subscriptions.retry-in-progress-after = "1s""""
  )
  private val repository = new SubscriptionWorkItemRepository(repoConfig, mongoComponent)

  // Force a race-like condition deterministically by making `findByArnAndRegime` lie, while still relying on the
  // unique (arn, regime) index in Mongo to reject the insert.
  class RaceSubscriptionWorkItemRepository
  extends SubscriptionWorkItemRepository(repoConfig, mongoComponent):
    override def findByArnAndRegime(
      arn: Arn,
      regime: LegacyRegime
    ): Future[Option[uk.gov.hmrc.mongo.workitem.WorkItem[SubscriptionWorkItem]]] = Future.successful(None)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    repository.coll.drop().toFuture().futureValue
  }

  "startSubscriptionProcess" when {
    "invoked for PAYE" should {
      "capture session and bearer when stubs compatibility mode is enabled" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(true)
        when(connector.register(subscriptionRequest)(using testRequest)).thenReturn(Future.successful(testAgentRef))
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        service.startSubscriptionProcess(
          testArn,
          subscriptionRequest,
          PAYE,
          testAdminCredId,
          testGroupId
        )(using testRequest).futureValue

        val item = repository.coll.find().first().toFuture().futureValue.item

        item.sessionId shouldBe Some("session-123")
        item.bearerToken shouldBe Some("Bearer test-token")
      }

      "omit session and bearer when stubs compatibility mode is disabled" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(connector.register(subscriptionRequest)(using testRequest)).thenReturn(Future.successful(testAgentRef))
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        service.startSubscriptionProcess(
          testArn,
          subscriptionRequest,
          PAYE,
          testAdminCredId,
          testGroupId
        )(using testRequest).futureValue

        val item = repository.coll.find().first().toFuture().futureValue.item

        item.sessionId shouldBe None
        item.bearerToken shouldBe None
      }
    }
    "invoked for SA" should {
      "capture session and bearer when stubs compatibility mode is enabled" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(true)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        service.startSubscriptionProcess(
          testArn,
          saSubscriptionRequest,
          SA,
          testAdminCredId,
          testGroupId
        )(using testRequest).futureValue

        val item = repository.coll.find().first().toFuture().futureValue.item

        item.sessionId shouldBe Some("session-123")
        item.bearerToken shouldBe Some("Bearer test-token")
        item.regime shouldBe LegacyRegime.SA
      }

      "fail when SA enrolment already exists on the group" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(
          Future.successful(List(Enrolment(service = LegacyRegime.SA.enrolmentKey, state = "Activated")))
        )

        val ex =
          service.startSubscriptionProcess(
            testArn,
            saSubscriptionRequest,
            SA,
            testAdminCredId,
            testGroupId
          )(using testRequest).failed.futureValue

        ex shouldBe a[uk.gov.hmrc.http.UpstreamErrorResponse]
      }

      "replace a permanently failed work item when SA subscription is retried" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        val failedItem =
          repository.pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = saSubscriptionRequest,
              regime = LegacyRegime.SA,
              agentReference = None,
              groupId = testGroupId,
              adminCredId = testAdminCredId
            )
          ).futureValue

        repository.markAs(failedItem.id, PermanentlyFailed).futureValue

        service.startSubscriptionProcess(
          testArn,
          saSubscriptionRequest,
          SA,
          testAdminCredId,
          testGroupId
        )(using testRequest).futureValue

        val items = repository.coll.find().toFuture().futureValue

        items.size.shouldBe(1)
        items.head.id.should(not(be(failedItem.id)))
        items.head.status.shouldBe(ToDo)
        items.head.item.regime.shouldBe(LegacyRegime.SA)
      }

      "return 429 when a concurrent SA start hits the unique (arn, regime) index" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]

        val raceRepository = new RaceSubscriptionWorkItemRepository
        val service =
          new SubscriptionService(
            connector,
            raceRepository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        // Seed an existing SA work item so the insert below will hit the unique index.
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = saSubscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = None,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue

        val ex =
          service.startSubscriptionProcess(
            testArn,
            saSubscriptionRequest,
            SA,
            testAdminCredId,
            testGroupId
          )(using testRequest).failed.futureValue

        ex shouldBe a[uk.gov.hmrc.http.UpstreamErrorResponse]
        ex.asInstanceOf[uk.gov.hmrc.http.UpstreamErrorResponse].statusCode shouldBe 429
      }
    }
    "invoked for CT" should {
      "capture session and bearer when stubs compatibility mode is enabled" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(true)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        service.startSubscriptionProcess(
          testArn,
          ctSubscriptionRequest,
          CT,
          testAdminCredId,
          testGroupId
        )(using testRequest).futureValue

        val item = repository.coll.find().first().toFuture().futureValue.item

        item.sessionId shouldBe Some("session-123")
        item.bearerToken shouldBe Some("Bearer test-token")
        item.regime shouldBe LegacyRegime.CT
      }

      "fail when SA enrolment already exists on the group" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(
          Future.successful(List(Enrolment(service = LegacyRegime.CT.enrolmentKey, state = "Activated")))
        )

        val ex =
          service.startSubscriptionProcess(
            testArn,
            ctSubscriptionRequest,
            CT,
            testAdminCredId,
            testGroupId
          )(using testRequest).failed.futureValue

        ex shouldBe a[uk.gov.hmrc.http.UpstreamErrorResponse]
      }

      "replace a permanently failed work item when SA subscription is retried" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]
        val service =
          new SubscriptionService(
            connector,
            repository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        val failedItem =
          repository.pushNew(
            SubscriptionWorkItem(
              arn = testArn,
              subscriptionRequest = saSubscriptionRequest,
              regime = LegacyRegime.CT,
              agentReference = None,
              groupId = testGroupId,
              adminCredId = testAdminCredId
            )
          ).futureValue

        repository.markAs(failedItem.id, PermanentlyFailed).futureValue

        service.startSubscriptionProcess(
          testArn,
          ctSubscriptionRequest,
          CT,
          testAdminCredId,
          testGroupId
        )(using testRequest).futureValue

        val items = repository.coll.find().toFuture().futureValue

        items.size.shouldBe(1)
        items.head.id.should(not(be(failedItem.id)))
        items.head.status.shouldBe(ToDo)
        items.head.item.regime.shouldBe(LegacyRegime.CT)
      }

      "return 429 when a concurrent SA start hits the unique (arn, regime) index" in {
        val connector = mock[AgentEpayeRegistrationConnector]
        val appConfig = mock[AppConfig]
        val espConnector = mock[EnrolmentStoreProxyConnector]
        val agentMappingConnector = mock[AgentMappingConnector]

        val raceRepository = new RaceSubscriptionWorkItemRepository
        val service =
          new SubscriptionService(
            connector,
            raceRepository,
            espConnector,
            agentMappingConnector,
            appConfig
          )

        when(appConfig.stubsCompatibilityMode).thenReturn(false)
        when(espConnector.queryEnrolmentsAllocatedToGroup(testGroupId)(using testRequest)).thenReturn(Future.successful(Nil))

        // Seed an existing SA work item so the insert below will hit the unique index.
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = saSubscriptionRequest,
            regime = LegacyRegime.CT,
            agentReference = None,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue

        val ex =
          service.startSubscriptionProcess(
            testArn,
            ctSubscriptionRequest,
            CT,
            testAdminCredId,
            testGroupId
          )(using testRequest).failed.futureValue

        ex shouldBe a[uk.gov.hmrc.http.UpstreamErrorResponse]
        ex.asInstanceOf[uk.gov.hmrc.http.UpstreamErrorResponse].statusCode shouldBe 429
      }
    }
  }

  "handleRoboticsCallback" should {
    "set agentReference and return the work item to ToDo on successful callback" in {
      val connector = mock[AgentEpayeRegistrationConnector]
      val appConfig = mock[AppConfig]
      val espConnector = mock[EnrolmentStoreProxyConnector]
      val agentMappingConnector = mock[AgentMappingConnector]
      val service =
        new SubscriptionService(
          connector,
          repository,
          espConnector,
          agentMappingConnector,
          appConfig
        )

      val requestId = "sa-callback-success-request-id"
      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = saSubscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = None,
            requestId = requestId,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue
      repository.markAs(workItem.id, InProgress).futureValue

      val updated =
        service.handleRoboticsCallback(
          SubscriptionCallback(
            requestId = requestId,
            targetSystem = TargetSystem.CESA,
            operationRequired = Operation.CREATE,
            agentId = Some(testAgentRef),
            status = CallbackStatus.CallbackSuccess,
            requestMessage = "ok"
          )
        ).futureValue

      updated shouldBe SubscriptionService.CallbackHandling.Handled
      val persisted = repository.coll.find().first().toFuture().futureValue
      persisted.status shouldBe ToDo
      persisted.item.agentReference shouldBe Some(testAgentRef)
    }

    "ignore a success callback if the work item is already PermanentlyFailed" in {
      val connector = mock[AgentEpayeRegistrationConnector]
      val appConfig = mock[AppConfig]
      val espConnector = mock[EnrolmentStoreProxyConnector]
      val agentMappingConnector = mock[AgentMappingConnector]
      val service =
        new SubscriptionService(
          connector,
          repository,
          espConnector,
          agentMappingConnector,
          appConfig
        )

      val requestId = "sa-callback-success-after-failure-request-id"
      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = saSubscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = None,
            requestId = requestId,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue
      repository.markAs(workItem.id, PermanentlyFailed).futureValue

      val updated =
        service.handleRoboticsCallback(
          SubscriptionCallback(
            requestId = requestId,
            targetSystem = TargetSystem.CESA,
            operationRequired = Operation.CREATE,
            agentId = Some(testAgentRef),
            status = CallbackStatus.CallbackSuccess,
            requestMessage = "ok"
          )
        ).futureValue

      updated shouldBe SubscriptionService.CallbackHandling.Handled
      val persisted = repository.coll.find().first().toFuture().futureValue
      persisted.status shouldBe PermanentlyFailed
      persisted.item.agentReference shouldBe None
    }

    "ignore a failure callback if success has already been recorded" in {
      val connector = mock[AgentEpayeRegistrationConnector]
      val appConfig = mock[AppConfig]
      val espConnector = mock[EnrolmentStoreProxyConnector]
      val agentMappingConnector = mock[AgentMappingConnector]
      val service =
        new SubscriptionService(
          connector,
          repository,
          espConnector,
          agentMappingConnector,
          appConfig
        )

      val requestId = "sa-callback-failure-after-success-request-id"
      val workItem =
        repository.pushNew(
          SubscriptionWorkItem(
            arn = testArn,
            subscriptionRequest = saSubscriptionRequest,
            regime = LegacyRegime.SA,
            agentReference = None,
            requestId = requestId,
            groupId = testGroupId,
            adminCredId = testAdminCredId
          )
        ).futureValue
      repository.markAs(workItem.id, InProgress).futureValue

      service.handleRoboticsCallback(
        SubscriptionCallback(
          requestId = requestId,
          targetSystem = TargetSystem.CESA,
          operationRequired = Operation.CREATE,
          agentId = Some(testAgentRef),
          status = CallbackStatus.CallbackSuccess,
          requestMessage = "ok"
        )
      ).futureValue shouldBe SubscriptionService.CallbackHandling.Handled

      val updated =
        service.handleRoboticsCallback(
          SubscriptionCallback(
            requestId = requestId,
            targetSystem = TargetSystem.CESA,
            operationRequired = Operation.CREATE,
            agentId = Some(testAgentRef),
            status = CallbackStatus.CallbackFailure,
            requestMessage = "boom"
          )
        ).futureValue

      updated shouldBe SubscriptionService.CallbackHandling.Handled
      val persisted = repository.coll.find().first().toFuture().futureValue
      persisted.status shouldBe ToDo
      persisted.item.agentReference shouldBe Some(testAgentRef)
    }
  }

}
