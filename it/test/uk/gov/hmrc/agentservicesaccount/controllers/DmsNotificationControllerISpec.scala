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

package uk.gov.hmrc.agentservicesaccount.controllers

import play.api.http.Status.{BAD_REQUEST, OK, UNAUTHORIZED}
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.models.dms.{DmsNotification, SubmissionItemStatus}
import uk.gov.hmrc.agentservicesaccount.stubs.InternalAuthStub
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

class DmsNotificationControllerISpec
extends ComponentSpecHelper
with InternalAuthStub:

  val url = "/dms-agent-callback"

  "POST /dms-notification/callback" should:

    "return 200 OK for a valid notification" in:
      stubInternalAuthorised()

      val response =
        post(url)(DmsNotification(
          "test123",
          SubmissionItemStatus.Submitted,
          None
        ))

      response.status shouldBe OK

    "return 400 BAD_REQUEST for invalid JSON" in:
      stubInternalAuthorised()

      val response = post(url)(Json.obj("invalid" -> "payload"))
      response.status shouldBe BAD_REQUEST

    "return 401 UNAUTHORIZED if no internal auth header present" in:
      val response = post(url, defaultHeaders = Seq("Content-Type" -> "application/json"))("")
      response.status shouldBe UNAUTHORIZED
