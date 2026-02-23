/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.controllers

import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackFailure
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackSuccess
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.Operation.CREATE
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem.CESA
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem.COTAX
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.stubs.AgentAuthStubs
import uk.gov.hmrc.agentservicesaccount.stubs.AgentEpayeRegistrationStubs
import uk.gov.hmrc.agentservicesaccount.stubs.AgentMappingStubs
import uk.gov.hmrc.agentservicesaccount.stubs.EnrolmentStoreProxyStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.Deferred
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.PermanentlyFailed

import java.util.UUID

class LegacySubscriptionControllerISpec
extends ComponentSpecHelper
with AgentEpayeRegistrationStubs
with AgentMappingStubs
with EnrolmentStoreProxyStubs
with AgentAuthStubs:

  lazy val repository: SubscriptionWorkItemRepository = app.injector.instanceOf[SubscriptionWorkItemRepository]

  override def beforeEach(): Unit =
    repository.coll.drop().head().futureValue
    super.beforeEach()

  val testArn = Arn("AARN0000001")
  val testAgentName = "Test Agency"
  val testContactName = "John Agent"
  val testPhoneNumber = "1234567890"
  val testEmail = "test@email.com"
  val testPostCode = "A11 11A"
  val testAddress = SubscriptionAddress(
    line1 = "Line 1",
    line2 = "Line 2",
    line3 = Some("Line 3"),
    line4 = Some("Line 4"),
    postCode = Some(testPostCode)
  )

  val testAgentReference = AgentReference("AB1234")

  val testPayeSubscriptionRequest: PayeSubscriptionRequest = PayeSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAddress
  )
  val testSaSubscriptionRequest: SubscriptionRequest = SaSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAddress,
    isAbroad = false
  )
  val testCtSubscriptionRequest: SubscriptionRequest = CtSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAddress,
    isAbroad = false
  )
  "POST /legacy-subscription-request/:regime" should:
    "return 200 after successfully calling OPRA and creating a new work item for PAYE regime" in:
      isLoggedInAsASAgent(testArn)

      givenEpayeRegisterCallSucceeds(testPayeSubscriptionRequest)(testAgentReference)

      val response = post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(testPayeSubscriptionRequest)

      response.status shouldBe 200
      repository.coll.find().headOption().futureValue.nonEmpty shouldBe true

    "throw error after a failed OPRA call without creating a new work item for PAYE regime" in:
      isLoggedInAsASAgent(testArn)

      givenEpayeRegisterCallFails(testPayeSubscriptionRequest)

      val response = post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(testPayeSubscriptionRequest)

      response.status shouldBe 400
      repository.coll.find().headOption().futureValue.flatMap(_.item.agentReference) shouldBe None

    "return 501 for SA regime" in:
      isLoggedInAsASAgent(testArn)

      givenEs3CallSucceeds(testGroupId)()
      val response = post(s"/legacy-subscription-request/$SA")(testSaSubscriptionRequest)

      response.status shouldBe 200
      repository.coll.find().headOption().futureValue.map(_.item.regime) shouldBe Some(SA)

    "return 501 for CT regime" in:
      isLoggedInAsASAgent(testArn)

      val response = post(s"/legacy-subscription-request/$CT")(testCtSubscriptionRequest)

      response.status shouldBe 501

  "GET /legacy-subscription-info" should:
    "return 200 with the correct information for an in progress work item" in:
      isLoggedInAsASAgent(testArn)

      repository.pushNew(SubscriptionWorkItem(
        testArn,
        testSaSubscriptionRequest,
        SA,
        None
      )).futureValue

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = SA,
          subscriptionStatus = SubscriptionStatus.SubscriptionInProgress
        )
      )

    "return 200 with the correct information for a permanently failed work item" in:
      isLoggedInAsASAgent(testArn)

      val model =
        repository.pushNew(SubscriptionWorkItem(
          testArn,
          testSaSubscriptionRequest,
          SA,
          None
        )).futureValue

      repository.markAs(model.id, PermanentlyFailed).futureValue

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = SA,
          subscriptionStatus = SubscriptionStatus.SubscriptionFailed
        )
      )

    "return 200 with the correct information for a deferred work item" in:
      isLoggedInAsASAgent(testArn)

      val model =
        repository.pushNew(SubscriptionWorkItem(
          testArn,
          testSaSubscriptionRequest,
          SA,
          None
        )).futureValue

      repository.markAs(model.id, Deferred).futureValue

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = SA,
          subscriptionStatus = SubscriptionStatus.SubscriptionInProgress
        )
      )

    "return 200 with the correct information for an agency with an existing subscription" in:
      isLoggedInAsASAgent(testArn)
      givenEs3CallSucceeds(testGroupId)(PAYE)

      val response = get(s"/legacy-subscription-info?regimes=${PAYE.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = PAYE,
          subscriptionStatus = SubscriptionStatus.SubscriptionOnAgency
        )
      )
    "return 200 with the correct information for an agency with an existing mapping" in:
      isLoggedInAsASAgent(testArn)
      givenEs3CallSucceeds(testGroupId)()
      givenGetMappingsCallSucceeds(testArn, CT)(testAgentReference)

      val response = get(s"/legacy-subscription-info?regimes=${CT.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = CT,
          subscriptionStatus = SubscriptionStatus.SubscriptionMapped
        )
      )
    "return 200 with the correct information for an agency with no subscription info" in:
      isLoggedInAsASAgent(testArn)
      givenEs3CallSucceeds(testGroupId)()
      givenGetMappingsCallSucceeds(testArn, SA)()

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = SA,
          subscriptionStatus = SubscriptionStatus.NotSubscribed
        )
      )

    "return 200 with the correct information for a multiple different subscriptions in different states" in:
      isLoggedInAsASAgent(testArn)
      repository.pushNew(SubscriptionWorkItem(
        testArn,
        testSaSubscriptionRequest,
        SA,
        None
      )).futureValue
      givenEs3CallSucceeds(testGroupId)(CT)
      givenGetMappingsCallSucceeds(testArn, PAYE)(testAgentReference)

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}&regimes=${CT.toString}&regimes=${PAYE.toString}")

      response.status shouldBe 200
      response.json.as[Seq[SubscriptionInfo]] shouldBe Seq(
        SubscriptionInfo(
          regime = SA,
          subscriptionStatus = SubscriptionStatus.SubscriptionInProgress
        ),
        SubscriptionInfo(
          regime = CT,
          subscriptionStatus = SubscriptionStatus.SubscriptionOnAgency
        ),
        SubscriptionInfo(
          regime = PAYE,
          subscriptionStatus = SubscriptionStatus.SubscriptionMapped
        )
      )

  "POST /robotics/callback" should:
    "return 204 when a work item is successfully updated with the new agentReference from a successful callback" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = testAgentReference,
        status = CallbackSuccess,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = None,
        requestId = requestId
      )).futureValue
      val expected = SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = Some(testAgentReference),
        requestId = requestId
      )

      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 204
      repository.coll.find().headOption().futureValue.map(_.item) shouldBe Some(expected)

    "return 204 when a work item is already updated by a prior a successful callback" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = testAgentReference,
        status = CallbackSuccess,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = None,
        requestId = requestId
      )).futureValue
      val expected = SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = Some(testAgentReference),
        requestId = requestId
      )

      post(s"/robotics/callback")(testCallbackRequest)
      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 204
      repository.coll.find().headOption().futureValue.map(_.item) shouldBe Some(expected)

    "return 204 when a work item is successfully set to permanently failed state from a failed callback" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = COTAX,
        operationRequired = CREATE,
        agentId = AgentReference(""),
        status = CallbackFailure,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testCtSubscriptionRequest,
        regime = CT,
        agentReference = None,
        requestId = requestId
      )).futureValue

      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 204
      repository.coll.find().headOption().futureValue.map(_.status) shouldBe Some(PermanentlyFailed)

    "return 204 when a work item is already updated by a prior failed callback" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = COTAX,
        operationRequired = CREATE,
        agentId = AgentReference(""),
        status = CallbackFailure,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testCtSubscriptionRequest,
        regime = CT,
        agentReference = None,
        requestId = requestId
      )).futureValue

      post(s"/robotics/callback")(testCallbackRequest)
      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 204
      repository.coll.find().headOption().futureValue.map(_.status) shouldBe Some(PermanentlyFailed)

    "return 404 when no work item is found for the given correlationId" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = testAgentReference,
        status = CallbackSuccess,
        requestMessage = "test-message"
      )

      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 404

    "return 400 when the payload is invalid" in:
      val response = post(s"/robotics/callback")(Json.obj("invalid" -> "payload"))

      response.status shouldBe 400
