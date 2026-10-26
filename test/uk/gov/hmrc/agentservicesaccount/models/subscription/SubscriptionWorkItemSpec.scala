/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.models.subscription

import play.api.libs.json.*
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentRegime.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

class SubscriptionWorkItemSpec
extends UnitSpec:

  given Encrypter & Decrypter = SymmetricCryptoFactory.aesGcmCrypto("edkOOwt7uvzw1TXnFIN6aRVHkfWcgiOrbBvkEQvO65g=")

  val testArn = Arn("AARN0000001")
  val testAgentName = "Test Agency"
  val testContactName = "John Agent"
  val testPhoneNumber = "1234567890"
  val testEmail = "test@email.com"
  val testPostCode = "A11 11A"
  val testGroupId = GroupId("test-group-id")
  val testAdminCredId = CredId("test-cred-id")
  val testUkAddress = SubscriptionAddress(
    line1 = "Line 1",
    line2 = "Line 2",
    line3 = Some("Line 3"),
    line4 = Some("Line 4"),
    postCode = Some(testPostCode)
  )
  val testAgentReference = AgentReference("AB1234")
  val testPayeSubscriptionRequest = PayeSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testUkAddress,
    isWelsh = false
  )
  val testModel = SubscriptionWorkItem(
    arn = testArn,
    subscriptionRequest = testPayeSubscriptionRequest,
    regime = PAYE,
    agentReference = Some(testAgentReference),
    groupId = testGroupId,
    adminCredId = testAdminCredId,
    requestId = "test-request-id",
    sessionId = Some("session-123"),
    bearerToken = Some("Bearer test-token")
  )

  private def jsonFor(model: SubscriptionWorkItem): JsObject = Json.toJson(model)(using SubscriptionWorkItem.mongoFormat).as[JsObject]

  "SubscriptionWorkItem" should:
    "serialize to JSON correctly" in:
      val json = jsonFor(testModel)

      json("arn") shouldBe JsString("AARN0000001")
      json("regime") shouldBe JsString("PAYE")
      json("agentReference") shouldBe JsString("AB1234")
      json("groupId") shouldBe JsString("test-group-id")
      json("adminCredId") shouldBe JsString("test-cred-id")
      json("requestId") shouldBe JsString("test-request-id")
      json("sessionId") shouldBe JsString("session-123")
      json("bearerToken") shouldBe JsString("Bearer test-token")
      json("entityType") shouldBe JsString("Unknown")
      json("subscriptionRequest") shouldBe a[JsString]
      json("subscriptionRequest").as[String] should not be empty

    "deserialize from JSON correctly" in:
      val json = jsonFor(testModel)
      val result = Json.fromJson[SubscriptionWorkItem](json)(using SubscriptionWorkItem.mongoFormat)

      result shouldBe JsSuccess(testModel)

    "deserialize existing persisted JSON without entityType" in:
      val json = jsonFor(testModel) - "entityType"
      val result = Json.fromJson[SubscriptionWorkItem](json)(using SubscriptionWorkItem.mongoFormat)

      result shouldBe JsSuccess(testModel)

    "deserialize persisted PAYE work item when postcode is blank" in:
      val blankPostcodeModel = testModel.copy(
        subscriptionRequest = testPayeSubscriptionRequest.copy(
          address = testUkAddress.copy(postCode = Some("   "))
        )
      )
      val blankPostcodeJson = jsonFor(blankPostcodeModel)

      val result = Json.fromJson[SubscriptionWorkItem](blankPostcodeJson)(using SubscriptionWorkItem.mongoFormat)

      result shouldBe JsSuccess(blankPostcodeModel)
