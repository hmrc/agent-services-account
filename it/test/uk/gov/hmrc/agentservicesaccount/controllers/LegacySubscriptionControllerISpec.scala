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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.stubs.AgentAuthStubs
import uk.gov.hmrc.agentservicesaccount.stubs.AgentEpayeRegistrationStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

class LegacySubscriptionControllerISpec
extends ComponentSpecHelper
with AgentEpayeRegistrationStubs
with AgentAuthStubs:

  lazy val repository: SubscriptionWorkItemRepository = app.injector.instanceOf[SubscriptionWorkItemRepository]

  override def beforeEach(): Unit =
    repository.collection.drop().head().futureValue
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

  "POST /legacy-subscription-request/:regime" should:
    "return 200 after successfully calling OPRA and creating a new work item for PAYE regime" in:
      isLoggedInAsASAgent(testArn)
      val testSubscriptionRequest = PayeSubscriptionRequest(
        agentName = testAgentName,
        contactName = testContactName,
        phoneNumber = Some(testPhoneNumber),
        emailAddress = Some(testEmail),
        address = testAddress
      )
      val expected = SubscriptionWorkItem(
        arn = testArn,
        subscriptionRequest = testSubscriptionRequest,
        regime = PAYE,
        agentReference = Some(testAgentReference),
        sessionId = None
      )

      givenEpayeRegisterCallSucceeds(testSubscriptionRequest)(testAgentReference)

      val response = post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(testSubscriptionRequest)

      response.status shouldBe 200
      repository.collection.find().headOption().futureValue.map(_.item) shouldBe Some(expected)

    "throw error after a failed OPRA call without creating a new work item for PAYE regime" in:
      isLoggedInAsASAgent(testArn)
      val testSubscriptionRequest: PayeSubscriptionRequest = PayeSubscriptionRequest(
        agentName = testAgentName,
        contactName = testContactName,
        phoneNumber = Some(testPhoneNumber),
        emailAddress = Some(testEmail),
        address = testAddress
      )

      givenEpayeRegisterCallFails(testSubscriptionRequest)

      val response = post[SubscriptionRequest](s"/legacy-subscription-request/$PAYE")(testSubscriptionRequest)

      response.status shouldBe 400
      repository.collection.find().headOption().futureValue.flatMap(_.item.agentReference) shouldBe None

    "return 501 for SA regime" in:
      isLoggedInAsASAgent(testArn)
      val testSubscriptionRequest: SubscriptionRequest = SaSubscriptionRequest(
        agentName = testAgentName,
        contactName = testContactName,
        phoneNumber = Some(testPhoneNumber),
        emailAddress = Some(testEmail),
        address = testAddress,
        isAbroad = false
      )
      val response = post(s"/legacy-subscription-request/$SA")(testSubscriptionRequest)

      response.status shouldBe 501

    "return 501 for CT regime" in:
      isLoggedInAsASAgent(testArn)
      val testSubscriptionRequest: SubscriptionRequest = CtSubscriptionRequest(
        agentName = testAgentName,
        contactName = testContactName,
        phoneNumber = Some(testPhoneNumber),
        emailAddress = Some(testEmail),
        address = testAddress,
        isAbroad = false
      )
      val response = post(s"/legacy-subscription-request/$CT")(testSubscriptionRequest)

      response.status shouldBe 501
