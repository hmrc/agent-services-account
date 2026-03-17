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
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackFailure
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackSuccess
import uk.gov.hmrc.agentservicesaccount.models.subscription.Operation.CREATE
import uk.gov.hmrc.agentservicesaccount.models.subscription.Operation.UPDATE
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem.CESA
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem.COTAX
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class SubscriptionCallbackSpec
extends UnitSpec:

  val testCallback = SubscriptionCallback(
    requestId = "test-request-id",
    targetSystem = CESA,
    operationRequired = CREATE,
    agentId = Some(AgentReference("ABC123")),
    status = CallbackSuccess,
    requestMessage = "test-message"
  )

  val testCallback2 = SubscriptionCallback(
    requestId = "test-request-id",
    targetSystem = COTAX,
    operationRequired = UPDATE,
    agentId = Some(AgentReference("ABC123")),
    status = CallbackFailure,
    requestMessage = "test-message"
  )

  val testCallbackJson: JsObject = Json.obj(
    "requestId" -> "test-request-id",
    "targetSystem" -> "CESA",
    "operationRequired" -> "CREATE",
    "agentId" -> "ABC123",
    "status" -> "success",
    "requestMessage" -> "test-message"
  )

  val testCallbackJson2: JsObject = Json.obj(
    "requestId" -> "test-request-id",
    "targetSystem" -> "COTAX",
    "operationRequired" -> "UPDATE",
    "agentId" -> "ABC123",
    "status" -> "failure",
    "requestMessage" -> "test-message"
  )

  "SubscriptionCallback" should:
    "serialize to JSON correctly" in:
      val json = Json.toJson[SubscriptionCallback](testCallback)
      val json2 = Json.toJson[SubscriptionCallback](testCallback2)

      json shouldBe testCallbackJson
      json2 shouldBe testCallbackJson2

    "deserialize from JSON correctly" in:
      val result = Json.fromJson[SubscriptionCallback](testCallbackJson)
      val result2 = Json.fromJson[SubscriptionCallback](testCallbackJson2)

      result shouldBe JsSuccess(testCallback)
      result2 shouldBe JsSuccess(testCallback2)
