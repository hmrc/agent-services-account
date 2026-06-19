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
import play.api.libs.ws.WSBodyReadables.readableAsString
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.models.CredId
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
import uk.gov.hmrc.agentservicesaccount.stubs.DesStubs
import uk.gov.hmrc.agentservicesaccount.stubs.EnrolmentStoreProxyStubs
import uk.gov.hmrc.agentservicesaccount.stubs.HipStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.Deferred
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.PermanentlyFailed

import java.util.UUID
import org.scalatest.OptionValues.*

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LegacySubscriptionControllerISpec
extends ComponentSpecHelper
with AgentEpayeRegistrationStubs
with AgentMappingStubs
with EnrolmentStoreProxyStubs
with HipStubs
with DesStubs
with AgentAuthStubs:

  lazy val repository: SubscriptionWorkItemRepository = app.injector.instanceOf[SubscriptionWorkItemRepository]

  override def beforeEach(): Unit =
    repository.coll.drop().head().futureValue
    super.beforeEach()

  val testArn = Arn("AARN0000001")
  val testUtr = Utr("7000000002")
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
  val testCredId = CredId("test-cred-id")

  val testAgentReference = AgentReference("AB1234")

  val testPayeSubscriptionRequest: PayeSubscriptionRequest = PayeSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAddress,
    isWelsh = false
  )
  val testSaSubscriptionRequest: SubscriptionRequest = SaSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAddress,
    isAbroad = false,
    isWelsh = false
  )
  val testCtSubscriptionRequest: SubscriptionRequest = CtSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAddress,
    isAbroad = false,
    isWelsh = false
  )
  "POST /legacy-subscription-request/:regime" should:
    "return 200 after successfully calling OPRA and creating a new work item for PAYE regime" in:
      isLoggedInAsASAgent(testArn)

      givenEs3CallSucceeds(testGroupId)()
      givenEpayeRegisterCallSucceeds(testPayeSubscriptionRequest)(testAgentReference)

      val response = post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(testPayeSubscriptionRequest)

      response.status shouldBe 200
      repository.coll.find().headOption().futureValue.map(_.item.regime) shouldBe Some(PAYE)
      repository.coll.find().headOption().futureValue.flatMap(_.item.agentReference) shouldBe Some(testAgentReference)

    "throw error after a failed OPRA call without creating a new work item for PAYE regime" in:
      isLoggedInAsASAgent(testArn)

      givenEs3CallSucceeds(testGroupId)()
      givenEpayeRegisterCallFails(testPayeSubscriptionRequest)

      val response = post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(testPayeSubscriptionRequest)

      response.status shouldBe 400
      repository.coll.find().headOption().futureValue shouldBe None

    "return 400 without creating a work item for PAYE when postcode is blank" in:
      isLoggedInAsASAgent(testArn)

      givenEs3CallSucceeds(testGroupId)()

      val response =
        post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(
          testPayeSubscriptionRequest.copy(address = testAddress.copy(postCode = Some("   ")))
        )

      response.status shouldBe 400
      response.body[String] should include("Postcode is required for legacy subscriptions in UK")
      repository.coll.find().headOption().futureValue shouldBe None

    "return 200 for SA regime" in:
      isLoggedInAsASAgent(testArn)

      givenEs3CallSucceeds(testGroupId)()
      givenHIPGetAgentRecordSuspendedAgent(testArn, s""""${testUtr.value}"""")
      givenDESGetRegistrationData(testUtr, isIndividual = true)
      val response = post(s"/legacy-subscription-request/$SA")(testSaSubscriptionRequest)

      response.status shouldBe 200
      repository.coll.find().headOption().futureValue.map(_.item.regime) shouldBe Some(SA)
      repository.coll.find().headOption().futureValue.map(_.item.entityType) shouldBe Some(AgentEntityType.SoleTrader)

    "return 200 for CT regime" in:
      isLoggedInAsASAgent(testArn)

      givenEs3CallSucceeds(testGroupId)()
      givenHIPGetAgentRecordSuspendedAgent(testArn, s""""${testUtr.value}"""")
      givenDESGetRegistrationData(testUtr, isIndividual = false)
      val response = post(s"/legacy-subscription-request/$CT")(testCtSubscriptionRequest)

      response.status shouldBe 200
      repository.coll.find().headOption().futureValue.map(_.item.regime) shouldBe Some(CT)
      repository.coll.find().headOption().futureValue.map(_.item.entityType) shouldBe Some(AgentEntityType.Unknown)

  "GET /legacy-subscription-info" should:
    "return 200 with the correct information for an in progress work item" in:
      isLoggedInAsASAgent(testArn)

      repository.pushNew(SubscriptionWorkItem(
        testArn,
        testSaSubscriptionRequest,
        SA,
        None,
        testGroupId,
        testCredId
      )).futureValue

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      val result = response.json.as[Seq[SubscriptionInfo]]

      result should have size 1

      val info = result.head
      info.regime shouldBe SA
      info.subscriptionStatus shouldBe SubscriptionStatus.SubscriptionInProgress
      info.creationDate shouldBe defined

    "return 200 with the correct information for a permanently failed work item" in:
      isLoggedInAsASAgent(testArn)
      givenEs3CallSucceeds(testGroupId)()
      givenGetMappingsCallSucceeds(testArn, SA)()

      val model =
        repository.pushNew(SubscriptionWorkItem(
          testArn,
          testSaSubscriptionRequest,
          SA,
          None,
          testGroupId,
          testCredId
        )).futureValue

      repository.markAs(model.id, PermanentlyFailed).futureValue

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      val result = response.json.as[Seq[SubscriptionInfo]]

      result should have size 1

      val info = result.head
      info.regime shouldBe SA
      info.subscriptionStatus shouldBe SubscriptionStatus.SubscriptionFailed
      info.creationDate shouldBe defined

    "return 200 with the correct information for a deferred work item" in:
      isLoggedInAsASAgent(testArn)

      val model =
        repository.pushNew(SubscriptionWorkItem(
          testArn,
          testSaSubscriptionRequest,
          SA,
          None,
          testGroupId,
          testCredId
        )).futureValue

      repository.markAs(model.id, Deferred).futureValue

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}")

      response.status shouldBe 200
      val result = response.json.as[Seq[SubscriptionInfo]]

      result should have size 1

      val info = result.head
      info.regime shouldBe SA
      info.subscriptionStatus shouldBe SubscriptionStatus.SubscriptionInProgress
      info.creationDate shouldBe defined

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
      val result = response.json.as[Seq[SubscriptionInfo]]

      result should have size 1

      val info = result.head
      info.regime shouldBe SA
      info.subscriptionStatus shouldBe SubscriptionStatus.NotSubscribed

    "return 200 with the correct information for a multiple different subscriptions in different states" in:
      isLoggedInAsASAgent(testArn)
      repository.pushNew(SubscriptionWorkItem(
        testArn,
        testSaSubscriptionRequest,
        SA,
        None,
        testGroupId,
        testCredId
      )).futureValue
      givenEs3CallSucceeds(testGroupId)(CT)
      givenGetMappingsCallSucceeds(testArn, PAYE)(testAgentReference)

      val response = get(s"/legacy-subscription-info?regimes=${SA.toString}&regimes=${CT.toString}&regimes=${PAYE.toString}")

      response.status shouldBe 200
      val result = response.json.as[Seq[SubscriptionInfo]]

      result should have size 3

      val sa = result.find(_.regime == SA).value
      sa.subscriptionStatus shouldBe SubscriptionStatus.SubscriptionInProgress
      sa.creationDate shouldBe defined

      val ct = result.find(_.regime == CT).value
      ct.subscriptionStatus shouldBe SubscriptionStatus.SubscriptionOnAgency
      ct.creationDate shouldBe None

      val paye = result.find(_.regime == PAYE).value
      paye.subscriptionStatus shouldBe SubscriptionStatus.SubscriptionMapped
      paye.creationDate shouldBe None

  "POST /robotics/callback" should:
    "return 204 when a work item is successfully updated with the new agentReference from a successful callback" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = Some(testAgentReference),
        status = CallbackSuccess,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = None,
        groupId = testGroupId,
        adminCredId = testCredId,
        requestId = requestId
      )).futureValue
      val expected = SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = Some(testAgentReference),
        groupId = testGroupId,
        adminCredId = testCredId,
        requestId = requestId
      )

      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 204
      repository.coll.find().headOption().futureValue.map { workItem =>
        workItem.item shouldBe expected
//        workItem.availableAt shouldBe
//          LocalDate.now().plusDays(1).atTime(LocalTime.parse("07:00")).atZone(ZoneId.of("Europe/London")).toInstant
      }

    "return 204 when a work item is already updated by a prior a successful callback" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = Some(testAgentReference),
        status = CallbackSuccess,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = None,
        groupId = testGroupId,
        adminCredId = testCredId,
        requestId = requestId
      )).futureValue
      val expected = SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSaSubscriptionRequest,
        regime = SA,
        agentReference = Some(testAgentReference),
        groupId = testGroupId,
        adminCredId = testCredId,
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
        agentId = None,
        status = CallbackFailure,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testCtSubscriptionRequest,
        regime = CT,
        agentReference = None,
        groupId = testGroupId,
        adminCredId = testCredId,
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
        agentId = None,
        status = CallbackFailure,
        requestMessage = "test-message"
      )
      repository.pushNew(SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testCtSubscriptionRequest,
        regime = CT,
        agentReference = None,
        groupId = testGroupId,
        adminCredId = testCredId,
        requestId = requestId
      )).futureValue

      post(s"/robotics/callback")(testCallbackRequest)
      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 204
      repository.coll.find().headOption().futureValue.map(_.status) shouldBe Some(PermanentlyFailed)

    "return 400 when a successful callback does not contain an 'agentId'" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = None,
        status = CallbackSuccess,
        requestMessage = "test-message"
      )

      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 400

    "return 404 when no work item is found for the given correlationId" in:
      val requestId = UUID.randomUUID().toString
      val testCallbackRequest = SubscriptionCallback(
        requestId = requestId,
        targetSystem = CESA,
        operationRequired = CREATE,
        agentId = Some(testAgentReference),
        status = CallbackSuccess,
        requestMessage = "test-message"
      )

      val response = post(s"/robotics/callback")(testCallbackRequest)

      response.status shouldBe 404

    "return 400 when the payload is invalid" in:
      val response = post(s"/robotics/callback")(Json.obj("invalid" -> "payload"))

      response.status shouldBe 400
