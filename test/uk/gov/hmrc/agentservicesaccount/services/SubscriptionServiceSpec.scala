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
import uk.gov.hmrc.agentservicesaccount.models.{CredId, GroupId}
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
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

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    repository.coll.drop().toFuture().futureValue
  }

  "startPayeSubscription" should {
    "capture session and bearer when stubs compatibility mode is enabled" in {
      val connector = mock[AgentEpayeRegistrationConnector]
      val appConfig = mock[AppConfig]
      val espConnector = mock[EnrolmentStoreProxyConnector]
      val agentMappingConnector = mock[AgentMappingConnector]
      val service = new SubscriptionService(connector, repository, espConnector, agentMappingConnector, appConfig)

      when(appConfig.stubsCompatibilityMode).thenReturn(true)
      when(connector.register(subscriptionRequest)(using testRequest)).thenReturn(Future.successful(testAgentRef))

      service.startPayeSubscription(testArn, subscriptionRequest, testAdminCredId, testGroupId)(using testRequest).futureValue

      val item = repository.coll.find().first().toFuture().futureValue.item

      item.sessionId shouldBe Some("session-123")
      item.bearerToken shouldBe Some("Bearer test-token")
    }

    "omit session and bearer when stubs compatibility mode is disabled" in {
      val connector = mock[AgentEpayeRegistrationConnector]
      val appConfig = mock[AppConfig]
      val espConnector = mock[EnrolmentStoreProxyConnector]
      val agentMappingConnector = mock[AgentMappingConnector]
      val service = new SubscriptionService(connector, repository, espConnector, agentMappingConnector, appConfig)

      when(appConfig.stubsCompatibilityMode).thenReturn(false)
      when(connector.register(subscriptionRequest)(using testRequest)).thenReturn(Future.successful(testAgentRef))

      service.startPayeSubscription(testArn, subscriptionRequest, testAdminCredId, testGroupId)(using testRequest).futureValue

      val item = repository.coll.find().first().toFuture().futureValue.item

      item.sessionId shouldBe None
      item.bearerToken shouldBe None
    }
  }
}
