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

package uk.gov.hmrc.agentservicesaccount.controllers

import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

class CacheControllerISpec
extends ComponentSpecHelper:

  private def url(arn: String): String = s"/cache-refresh/$arn"

  "POST /cache-refresh/:arn" should:
    "return 204" when:
      "the ARN is valid" in:
        val response = post(url("AARN0000002"))(Json.obj())

        response.status shouldBe NO_CONTENT

    "return 400" when:
      "the ARN is invalid" in:
        val response = post(url("invalid-arn"))(Json.obj())

        response.status shouldBe BAD_REQUEST
