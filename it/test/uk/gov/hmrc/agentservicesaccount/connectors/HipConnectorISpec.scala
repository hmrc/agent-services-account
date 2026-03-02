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
import uk.gov.hmrc.agentservicesaccount.stubs.HipStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.crypto.SymmetricCryptoFactory.aesCrypto
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext

class HipConnectorISpec
extends ComponentSpecHelper
with HipStubs {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  override def extraConfig: Map[String, Any] = Map(
    "microservice.services.hip.host" -> mockHost,
    "microservice.services.hip.port" -> mockPort,
    "microservice.services.hip.authorization-token" -> "secret",
    "http-verbs.retries.intervals" -> List("1ms"),
    "agent.entity.cache.enabled" -> true,
    "agent.entity.cache.expires" -> "1 second",
    "auditing.enabled" -> false
  )

  private implicit lazy val configuration: Config = app.injector.instanceOf[Config]
  private implicit lazy val as: ActorSystem = ActorSystem()

  private implicit val crypto: Encrypter
    with Decrypter = aesCrypto("0xbYzrPV9/GmVEGazywGswm7yRYoWy2BraeJnjOUgcY=")

  private def encryptKey(key: String): String = crypto.encrypt(PlainText(key)).value

  lazy val agentDataCache =
    new AgencyDetailsCacheRepository(
      app.injector.instanceOf[Configuration],
      mongoComponent,
      new CurrentTimestampSupport,
      app.injector.instanceOf[Metrics]
    )

  lazy val cacheProvider = new CacheProvider(agentDataCache, app.injector.instanceOf[Configuration])

  lazy val hipConnector =
    new HipConnector(
      app.injector.instanceOf[AppConfig],
      app.injector.instanceOf[HttpClientV2],
      cacheProvider,
      configuration,
      as
    )

  val arn = Arn("AARN00012345")

  val expectedResponse = AgentDetailsDesResponse(
    Some(Utr("123456")),
    Some(
      AgencyDetails(
        Some("ABC Accountants"),
        Some("abc@xyz.com"),
        Some("07345678901"),
        Some(
          BusinessAddress(
            "Matheson House",
            Some("Grange Central"),
            Some("Town Centre"),
            Some("Telford"),
            Some("TF3 4ER"),
            "GB"
          )
        )
      )
    ),
    Some(SuspensionDetails(suspensionStatus = true, None)),
    Some(true)
  )

  "HipConnector getAgentRecord" should {

    "return mapped agency details from HIP" in {
      givenHIPGetAgentRecordSuspendedAgent(arn)

      hipConnector.getAgentRecord(arn).futureValue shouldBe expectedResponse
    }

    "cache agency details after first call" in {
      givenHIPGetAgentRecordSuspendedAgent(arn)

      hipConnector.getAgentRecord(arn).futureValue shouldBe expectedResponse
      Thread.sleep(500)

      agentDataCache
        .getFromCache(cacheId = encryptKey(arn.value))
        .futureValue shouldBe Some(expectedResponse)
    }

    "return cached result on second call" in {
      givenHIPGetAgentRecordSuspendedAgent(arn)

      hipConnector.getAgentRecord(arn).futureValue
      Thread.sleep(500)
      hipConnector.getAgentRecord(arn).futureValue

      verifyHipGetAgentRecord(arn, 1)
    }

    "fail when HIP returns 5xx" in {
      givenHipReturnsServerError(arn)
      an[Exception] should be thrownBy await(hipConnector.getAgentRecord(arn))
    }

    "fail when HIP returns 404" in {
      givenHipAgentIsUnknown404(arn)
      an[Exception] should be thrownBy await(hipConnector.getAgentRecord(arn))
    }
  }

}
