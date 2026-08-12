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

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.AgencyDetails
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.models.BusinessAddress
import uk.gov.hmrc.agentservicesaccount.repositories.AgencyDetailsCacheRepository
import uk.gov.hmrc.agentservicesaccount.services.CacheProvider
import uk.gov.hmrc.agentservicesaccount.stubs.DataStreamStub
import uk.gov.hmrc.agentservicesaccount.stubs.DesStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.crypto.SymmetricCryptoFactory.aesCrypto
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext

class DesConnectorISpec
extends ComponentSpecHelper
with DesStubs
with DataStreamStub {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  override def extraConfig: Map[String, Any] = Map(
    "microservice.services.auth.host" -> mockHost,
    "microservice.services.auth.port" -> mockPort,
    "microservice.services.des.host" -> mockHost,
    "microservice.services.des.port" -> mockPort,
    "microservice.services.des.environment" -> "test",
    "microservice.services.des.authorization-token" -> "secret",
    "microservice.services.enrolment-store-proxy.host" -> mockHost,
    "microservice.services.enrolment-store-proxy.port" -> mockPort,
    "auditing.consumer.baseUri.host" -> mockHost,
    "auditing.consumer.baseUri.port" -> mockPort,
    "internal-auth-token-enabled-on-start" -> false,
    "http-verbs.retries.intervals" -> List("1ms"),
    "agent.entity.cache.enabled" -> true,
    "agent.entity.cache.expires" -> "1 second",
    "auditing.enabled" -> false,
    "rate-limiter.business-names.max-calls-per-second" -> 10
  )

  private implicit lazy val configuration: Config = app.injector.instanceOf[Config]
  private implicit lazy val as: ActorSystem = ActorSystem()

  lazy val desConnector =
    new DesConnector(
      app.injector.instanceOf[AppConfig],
      app.injector.instanceOf[HttpClientV2],
      configuration,
      as
    )

  val utr = Utr("1234567890")

  "DesConnector getRegistration" should {
    "post no-name-match lookup to the individual UTR path and return registration data" in {
      givenDESGetRegistrationData(utr, isIndividual = false)

      val result = desConnector.getRegistration(utr).futureValue

      result.map(_.isAnIndividual) shouldBe Some(false)
      result.flatMap(_.organisation.flatMap(_.organisationType)) shouldBe Some("Not Specified")
      verifyDESGetRegistrationData(utr, 1)
    }

    "return None when registration data is not found" in {
      givenDESGetRegistrationNotFound(utr)

      desConnector.getRegistration(utr).futureValue shouldBe None
      verifyDESGetRegistrationData(utr, 1)
    }
  }

}
