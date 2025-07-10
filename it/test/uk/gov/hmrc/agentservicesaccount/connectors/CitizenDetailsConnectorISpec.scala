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

package uk.gov.hmrc.agentservicesaccount.connectors

import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException
import uk.gov.hmrc.agentservicesaccount.stubs.CitizenDetailsStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext

class CitizenDetailsConnectorISpec
  extends ComponentSpecHelper
    with CitizenDetailsStubs {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  override def extraConfig: Map[String, Any] = Map(
    "microservice.services.citizen-details.host" -> mockHost,
    "microservice.services.citizen-details.port" -> mockPort,
    "auditing.enabled" -> false,
    "http-verbs.retries.intervals" -> List("1ms")
  )



  lazy val connector = new CitizenDetailsConnector(
    app.injector.instanceOf[AppConfig],
    app.injector.instanceOf[HttpClientV2]
  )

  val saUtrAlive = SaUtr("1234567890")
  val saUtrDeceased = SaUtr("9876543210")
  val saUtrError = SaUtr("9999999999")

  "CitizenDetailsConnector.getCitizenDeceasedFlag" should {
    "return None when citizen is alive" in {
      givenCitizenIsAlive(saUtrAlive)

      val result = await(connector.getCitizenDeceasedFlag(saUtrAlive))
      result shouldBe None
    }

    "return Some(DeceasedCheckException.EntityDeceasedCheckFailed) when citizen is deceased" in {
      givenCitizenIsDeceased(saUtrDeceased)

      val result = await(connector.getCitizenDeceasedFlag(saUtrDeceased))
      result shouldBe Some(DeceasedCheckException.EntityDeceasedCheckFailed)
    }

    "return Some(DeceasedCheckException.CitizenConnectorRequestFailed) when upstream error (e.g. 500)" in {
      givenCitizenDetailsReturnsError(saUtrError, 500)

      val result = await(connector.getCitizenDeceasedFlag(saUtrError))
      result shouldBe Some(DeceasedCheckException.CitizenConnectorRequestFailed(500))
    }

    "return Some(DeceasedCheckException.CitizenConnectorRequestFailed) for 404 response" in {
      givenCitizenDetailsReturnsError(saUtrError, 404)

      val result = await(connector.getCitizenDeceasedFlag(saUtrError))
      result shouldBe Some(DeceasedCheckException.CitizenConnectorRequestFailed(404))
    }
  }
}
