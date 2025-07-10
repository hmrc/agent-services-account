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

package uk.gov.hmrc.agentservicesaccount.connectors

import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.stubs.{DataStreamStub, EmailStub}
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext

class EmailConnectorISpec
  extends ComponentSpecHelper
    with DataStreamStub
    with EmailStub {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  lazy implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  lazy val connector: EmailConnector =
    new EmailConnector(
      appConfig,
      httpClient
    )

  override def extraConfig: Map[String, Any] = Map(
      "microservice.services.email.host" -> mockHost,
      "microservice.services.email.port" -> mockPort,
      "agent-maintainer-email" -> "test@example.com",
      "auditing.enabled" -> false
    )

  val emailInfo: EmailInformation = EmailInformation(
    to = Seq("abc@xyz.com"),
    templateId = "template-id",
    parameters = Map("param1" -> "foo", "param2" -> "bar")
  )

  "sendEmail" should {

    "return Unit when the email service responds" in {
      givenEmailSent(emailInfo)

      val result: Unit = await(connector.sendEmail(emailInfo))

      result shouldBe (())
    }

    "not throw an Exception when the email service throws an Exception" in {
      givenEmailReturns500

      val result: Unit = await(connector.sendEmail(emailInfo))

      result shouldBe (())
    }
  }

}
