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

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, OK, UNAUTHORIZED}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.agentservicesaccount.models.dms.{DmsNotification, SubmissionItemStatus}
import uk.gov.hmrc.agentservicesaccount.support.WireMockSupport
import uk.gov.hmrc.agentservicesaccount.stubs.InternalAuthStub
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String


import scala.concurrent.Await
import scala.concurrent.duration.*

class DmsNotificationControllerISpec
  extends PlaySpec
    with GuiceOneServerPerSuite
    with WireMockSupport
    with InternalAuthStub {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.internal-auth.host" -> wireMockHost,
      "microservice.services.internal-auth.port" -> wireMockPort,
      "internal-auth-token-enabled-on-start" -> false,
      "auditing.enabled" -> false
    )
    .build()

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  private val url = s"http://localhost:$port/agent-services-account/dms-agent-callback"

  private def post(payload: String, withAuth: Boolean = true): WSResponse = {
    val headers = Seq("Content-Type" -> "application/json") ++
        (if (withAuth) Seq("Authorization" -> "Bearer token") else Seq.empty)

    val request = wsClient.url(url).withHttpHeaders(headers *)

    Await.result(request.post(payload), 5.seconds)
  }

  "POST /agent-services-account/dms-notification/callback" should {

    "return 200 OK for a valid notification" in {
      stubInternalAuthorised()

      val json = Json.stringify(Json.toJson(DmsNotification("test123", SubmissionItemStatus.Submitted, None)))
      val response = post(json)

      response.status mustBe OK
    }

    "return 400 BAD_REQUEST for invalid JSON" in {
      stubInternalAuthorised()

      val response = post("""{"invalid":"payload"}""")
      response.status mustBe BAD_REQUEST
    }

    "return 401 UNAUTHORIZED if no internal auth header present" in {
      val response = post("""{}""", withAuth = false)
      response.status mustBe UNAUTHORIZED
    }
  }
}
