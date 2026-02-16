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
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.SA
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class SubscriptionInfoSpec
extends UnitSpec:

  def testModel(status: SubscriptionStatus) = SubscriptionInfo(
    regime = SA,
    subscriptionStatus = status
  )

  def testJson(status: SubscriptionStatus): JsObject = Json.obj(
    "regime" -> "SA",
    "subscriptionStatus" -> status.toString
  )

  "SubscriptionInfo" should:
    SubscriptionStatus.values.foreach { status =>
      s"serialize to JSON correctly for $status" in:
        val json = Json.toJson[SubscriptionInfo](testModel(status))

        json shouldBe testJson(status)

      s"deserialize from JSON correctly for $status" in:
        val result = Json.fromJson[SubscriptionInfo](testJson(status))

        result shouldBe JsSuccess(testModel(status))
    }
