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
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class SubscriptionRequestSpec
extends UnitSpec:

  val testArn = Arn("AARN0000001")
  val testAgentName = "Test Agency"
  val testContactName = "John Agent"
  val testPhoneNumber = "1234567890"
  val testEmail = "test@email.com"
  val testPostCode = "A11 11A"
  val testUkAddress = SubscriptionAddress(
    line1 = "Line 1",
    line2 = "Line 2",
    line3 = Some("Line 3"),
    line4 = Some("Line 4"),
    postCode = Some(testPostCode)
  )
  val testAbroadAddress = SubscriptionAddress(
    line1 = "Line 1",
    line2 = "Line 2",
    line3 = Some("Line 3"),
    line4 = Some("Line 4"),
    postCode = None
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
  val testSaSubscriptionRequest = SaSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testUkAddress,
    isAbroad = false,
    isWelsh = false
  )
  val testCtSubscriptionRequest = CtSubscriptionRequest(
    agentName = testAgentName,
    contactName = testContactName,
    phoneNumber = Some(testPhoneNumber),
    emailAddress = Some(testEmail),
    address = testAbroadAddress,
    isAbroad = true,
    isWelsh = false
  )
  val testPayeJson: JsObject = Json.obj(
    "agentName" -> testAgentName,
    "contactName" -> testContactName,
    "phoneNumber" -> testPhoneNumber,
    "emailAddress" -> testEmail,
    "address" -> Json.obj(
      "line1" -> "Line 1",
      "line2" -> "Line 2",
      "line3" -> "Line 3",
      "line4" -> "Line 4",
      "postCode" -> testPostCode
    ),
    "isWelsh" -> false
  )
  val testSaJson: JsObject = Json.obj(
    "agentName" -> testAgentName,
    "contactName" -> testContactName,
    "phoneNumber" -> testPhoneNumber,
    "emailAddress" -> testEmail,
    "address" -> Json.obj(
      "line1" -> "Line 1",
      "line2" -> "Line 2",
      "line3" -> "Line 3",
      "line4" -> "Line 4",
      "postCode" -> testPostCode
    ),
    "isAbroad" -> false,
    "isWelsh" -> false
  )
  val testCtJson: JsObject = Json.obj(
    "agentName" -> testAgentName,
    "contactName" -> testContactName,
    "phoneNumber" -> testPhoneNumber,
    "emailAddress" -> testEmail,
    "address" -> Json.obj(
      "line1" -> "Line 1",
      "line2" -> "Line 2",
      "line3" -> "Line 3",
      "line4" -> "Line 4"
    ),
    "isAbroad" -> true,
    "isWelsh" -> false
  )

  "SubscriptionRequest" should:
    "serialize to JSON correctly for PAYE" in:
      val json = Json.toJson[SubscriptionRequest](testPayeSubscriptionRequest)

      json shouldBe testPayeJson

    "serialize to JSON correctly for SA" in:
      val json = Json.toJson[SubscriptionRequest](testSaSubscriptionRequest)

      json shouldBe testSaJson

    "serialize to JSON correctly for CT" in:
      val json = Json.toJson[SubscriptionRequest](testCtSubscriptionRequest)

      json shouldBe testCtJson

    "deserialize from JSON correctly for PAYE" in:
      val result = Json.fromJson[SubscriptionRequest](testPayeJson)(using SubscriptionRequest.reads(PAYE))

      result shouldBe JsSuccess(testPayeSubscriptionRequest)

    "deserialize from JSON correctly for SA" in:
      val result = Json.fromJson[SubscriptionRequest](testSaJson)(using SubscriptionRequest.reads(SA))

      result shouldBe JsSuccess(testSaSubscriptionRequest)

    "deserialize from JSON correctly for CT" in:
      val result = Json.fromJson[SubscriptionRequest](testCtJson)(using SubscriptionRequest.reads(CT))

      result shouldBe JsSuccess(testCtSubscriptionRequest)

    "deserialize old PAYE JSON without isWelsh field" in {
      val oldJson = testPayeJson - "isWelsh"

      val result = Json.fromJson[SubscriptionRequest](oldJson)(using SubscriptionRequest.reads(PAYE))

      result shouldBe JsSuccess(testPayeSubscriptionRequest)
    }

    "deserialize old SA JSON without isWelsh field" in {
      val oldJson = testSaJson - "isWelsh"

      val result = Json.fromJson[SubscriptionRequest](oldJson)(using SubscriptionRequest.reads(SA))

      result shouldBe JsSuccess(testSaSubscriptionRequest)
    }

    "deserialize old CT JSON without isWelsh field" in {
      val oldJson = testCtJson - "isWelsh"

      val result = Json.fromJson[SubscriptionRequest](oldJson)(using SubscriptionRequest.reads(CT))

      result shouldBe JsSuccess(testCtSubscriptionRequest)
    }

    "treat missing isWelsh as false for PAYE" in {
      val json = testPayeJson - "isWelsh"

      val result = Json.fromJson[PayeSubscriptionRequest](json)

      result shouldBe JsSuccess(testPayeSubscriptionRequest)
    }

    "treat missing isWelsh as false for SA" in {
      val json = testSaJson - "isWelsh"

      val result = Json.fromJson[SaSubscriptionRequest](json)

      result shouldBe JsSuccess(testSaSubscriptionRequest)
    }

    "treat missing isWelsh as false for CT" in {
      val json = testCtJson - "isWelsh"

      val result = Json.fromJson[CtSubscriptionRequest](json)

      result shouldBe JsSuccess(testCtSubscriptionRequest)
    }

    "fail deserialization when postcode is missing for legacy UK subscription" in:
      val invalidSaJson = Json.obj(
        "agentName" -> testAgentName,
        "contactName" -> testContactName,
        "phoneNumber" -> testPhoneNumber,
        "emailAddress" -> testEmail,
        "address" -> Json.obj(
          "line1" -> "Line 1",
          "line2" -> "Line 2",
          "line3" -> "Line 3",
          "line4" -> "Line 4"
        ),
        "isAbroad" -> false,
        "isWelsh" -> false
      )

      val result = Json.fromJson[SubscriptionRequest](invalidSaJson)(using SubscriptionRequest.reads(SA))

      result shouldBe JsError("Postcode is required for legacy subscriptions in UK")

    "allow deserialization when postcode is blank for PAYE to preserve persisted work items" in:
      val payeJsonWithBlankPostcode = testPayeJson.deepMerge(Json.obj(
        "address" -> Json.obj(
          "postCode" -> "   "
        )
      ))

      val result = Json.fromJson[SubscriptionRequest](payeJsonWithBlankPostcode)(using SubscriptionRequest.reads(PAYE))

      result shouldBe JsSuccess(testPayeSubscriptionRequest.copy(address = testUkAddress.copy(postCode = Some("   "))))

    "fail inbound request deserialization when postcode is blank for PAYE" in:
      val payeJsonWithBlankPostcode = testPayeJson.deepMerge(Json.obj(
        "address" -> Json.obj(
          "postCode" -> "   "
        )
      ))

      val result = Json.fromJson[SubscriptionRequest](payeJsonWithBlankPostcode)(using SubscriptionRequest.requestReads(PAYE))

      result shouldBe JsError("Postcode is required for legacy subscriptions in UK")

    "fail inbound request deserialization when postcode is blank for legacy UK subscription" in:
      val saJsonWithBlankPostcode = testSaJson.deepMerge(Json.obj(
        "address" -> Json.obj(
          "postCode" -> "   "
        )
      ))

      val result = Json.fromJson[SubscriptionRequest](saJsonWithBlankPostcode)(using SubscriptionRequest.requestReads(SA))

      result shouldBe JsError("Postcode is required for legacy subscriptions in UK")
