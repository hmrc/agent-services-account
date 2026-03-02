/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Writes
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentservicesaccount.helpers.InstantClockTestSupport
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

trait ComponentSpecHelper
extends AnyWordSpec
with Matchers
with CustomMatchers
with WiremockHelper
with BeforeAndAfterAll
with BeforeAndAfterEach
with InstantClockTestSupport
with CleanMongoCollectionSupport
with GuiceOneServerPerSuite:

  def extraConfig: Map[String, Any] = Map.empty

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(300, Millis)))

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(config ++ extraConfig)
    .configure("play.http.router" -> "testOnlyDoNotUseInAppConf.Routes")
    .build()

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: String = WiremockHelper.wiremockPort.toString
  val mockUrl: String = s"http://$mockHost:$mockPort"

  def config: Map[String, String] = Map(
    "auditing.enabled" -> "false",
    "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
    "stubs-compatibility-mode" -> "false",
    "internal-auth-token-enabled-on-start" -> "false",
    "auditing.consumer.baseUri.host" -> mockHost,
    "auditing.consumer.baseUri.port" -> mockPort,
    "microservice.services.auth.host" -> mockHost,
    "microservice.services.auth.port" -> mockPort,
    "microservice.services.internal-auth.host" -> mockHost,
    "microservice.services.internal-auth.port" -> mockPort,
    "microservice.services.agent-epaye-registration.host" -> mockHost,
    "microservice.services.agent-epaye-registration.port" -> mockPort,
    "microservice.services.des.host" -> mockHost,
    "microservice.services.des.port" -> mockPort,
    "microservice.services.hip.host" -> mockHost,
    "microservice.services.hip.port" -> mockPort,
    "microservice.services.agent-assurance.host" -> mockHost,
    "microservice.services.agent-assurance.port" -> mockPort,
    "microservice.services.agent-mapping.host" -> mockHost,
    "microservice.services.agent-mapping.port" -> mockPort,
    "microservice.services.enrolment-store-proxy.host" -> mockHost,
    "microservice.services.enrolment-store-proxy.port" -> mockPort,
    "microservice.services.robotics.host" -> mockHost,
    "microservice.services.robotics.port" -> mockPort,
    "microservice.services.citizen-details.host" -> mockHost,
    "microservice.services.citizen-details.port" -> mockPort,
    "microservice.services.email.port" -> mockPort,
    "microservice.services.email.host" -> mockHost,
    "microservice.services.dms-submission.host" -> mockHost,
    "microservice.services.dms-submission.port" -> mockPort,
    "work-item-jobs.sa-robotics.enabled" -> "false"
  )

  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  override def beforeAll(): Unit =
    startWiremock()
    super.beforeAll()

  override def afterAll(): Unit =
    stopWiremock()
    super.afterAll()

  override def beforeEach(): Unit =
    resetWiremock()
    super.beforeEach()

  def get[T](
    uri: String,
    defaultHeaders: Seq[(String, String)] = Seq("Authorization" -> "Bearer 123")
  ): WSResponse = buildClient(uri).withHttpHeaders(defaultHeaders*).get().futureValue

  def post[T](
    uri: String,
    defaultHeaders: Seq[(String, String)] = Seq("Content-Type" -> "application/json", "Authorization" -> "Bearer 123")
  )(
    body: T,
    extraHeaders: Seq[(String, String)] = Nil
  )(implicit writes: Writes[T]): WSResponse =
    buildClient(uri)
      .withHttpHeaders(defaultHeaders*)
      .addHttpHeaders(extraHeaders*)
      .post(writes.writes(body).toString())
      .futureValue

  def postRaw(
    uri: String,
    defaultHeaders: Seq[(String, String)] = Seq("Content-Type" -> "application/json", "Authorization" -> "Bearer 123")
  )(
    body: String,
    extraHeaders: Seq[(String, String)] = Nil
  ): WSResponse =
    buildClient(uri)
      .withHttpHeaders(defaultHeaders*)
      .addHttpHeaders(extraHeaders*)
      .post(body)
      .futureValue

  def put[T](
    uri: String,
    defaultHeaders: Seq[(String, String)] = Seq("Content-Type" -> "application/json", "Authorization" -> "Bearer 123")
  )(
    body: T,
    extraHeaders: Seq[(String, String)] = Nil
  )(implicit writes: Writes[T]): WSResponse =
    buildClient(uri)
      .withHttpHeaders(defaultHeaders*)
      .addHttpHeaders(extraHeaders*)
      .put(writes.writes(body).toString())
      .futureValue

  def delete[T](
    uri: String,
    defaultHeaders: Seq[(String, String)] = Seq("Authorization" -> "Bearer 123")
  ): WSResponse = buildClient(uri).withHttpHeaders(defaultHeaders*).delete().futureValue

  val baseUrl: String = "/agent-services-account"

  def buildClient(path: String): WSRequest = ws.url(s"http://localhost:$port$baseUrl${path.replace(baseUrl, "")}").withFollowRedirects(false)
