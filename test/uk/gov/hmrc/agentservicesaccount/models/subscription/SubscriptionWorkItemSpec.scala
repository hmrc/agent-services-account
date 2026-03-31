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
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.SymmetricCryptoFactory

class SubscriptionWorkItemSpec
extends UnitSpec:

  given Encrypter & Decrypter = SymmetricCryptoFactory.aesCrypto("edkOOwt7uvzw1TXnFIN6aRVHkfWcgiOrbBvkEQvO65g=")

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
    address = testUkAddress
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
  val testJson: JsObject = Json.obj(
    "arn" -> "AARN0000001",
    "regime" -> "PAYE",
    "subscriptionRequest" -> "ibsRj/PwmBC+hnfD9XV14cpuk54MycnM5XHDSM+le0djqElGr3QtFK55VTegWQwOJXPlMHboVOm1zH0d0ZwaXUruPggPZCd7D6PcoLEYZf49TBIder8kSx7zasPZYcCOwHcdZX3k77tdEInV/Iyx/6LYP3IRvGI7DVm8MpvgKujabKcRTQSWh6beCsXGzzutoFGI2FUtHQS1GWhJbj17IfgXRwnDCgY+r5NsnxEIJpTQB/awiVs3UeLKXLWuHBPeK3SbTSDL5P7/JQSU6ZKLua2ZfyhkG7Z74Fn2RN984DI=",
    "agentReference" -> "AB1234",
    "groupId" -> "test-group-id",
    "adminCredId" -> "test-cred-id",
    "requestId" -> "test-request-id",
    "sessionId" -> "session-123",
    "bearerToken" -> "Bearer test-token"
  )

  "SubscriptionWorkItem" should:
    "serialize to JSON correctly" in:
      val json = Json.toJson(testModel)(SubscriptionWorkItem.mongoFormat)

      json shouldBe testJson

    "deserialize from JSON correctly" in:
      val result = Json.fromJson[SubscriptionWorkItem](testJson)(SubscriptionWorkItem.mongoFormat)

      result shouldBe JsSuccess(testModel)

    "deserialize persisted PAYE work item when postcode is blank" in:
      val blankPostcodeModel = testModel.copy(
        subscriptionRequest = testPayeSubscriptionRequest.copy(
          address = testUkAddress.copy(postCode = Some("   "))
        )
      )
      val blankPostcodeJson = Json.toJson(blankPostcodeModel)(SubscriptionWorkItem.mongoFormat)

      val result = Json.fromJson[SubscriptionWorkItem](blankPostcodeJson)(SubscriptionWorkItem.mongoFormat)

      result shouldBe JsSuccess(blankPostcodeModel)
