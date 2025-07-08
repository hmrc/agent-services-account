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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.{Application, Configuration}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, SuspensionDetails, Utr}
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.{AgencyDetails, AgentDetailsDesResponse, BusinessAddress}
import uk.gov.hmrc.agentservicesaccount.repositories.AgencyDetailsCacheRepository
import uk.gov.hmrc.agentservicesaccount.services.CacheProvider
import uk.gov.hmrc.agentservicesaccount.stubs.{DataStreamStub, DesStubs, MetricTestSupport}
import uk.gov.hmrc.agentservicesaccount.support.{UnitSpec, WireMockSupport}
import uk.gov.hmrc.crypto.SymmetricCryptoFactory.aesCrypto
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext

class DesConnectorISpec
extends UnitSpec
with GuiceOneAppPerSuite
with WireMockSupport
with DesStubs
with DataStreamStub
with CleanMongoCollectionSupport
with MetricTestSupport {
  
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()
  

  private implicit lazy val config: Config = app.injector.instanceOf[Config]
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


  lazy val cacheProvider =
    new CacheProvider(
      agentDataCache,
      app.injector.instanceOf[Configuration]
    )

  lazy val desConnector =
    new DesConnector(
      app.injector.instanceOf[AppConfig],
      app.injector.instanceOf[HttpClientV2],
      app.injector.instanceOf[Metrics],
      cacheProvider,
      config,
      as
    )

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.host" -> wireMockHost,
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.des.host" -> wireMockHost,
      "microservice.services.des.port" -> wireMockPort,
      "microservice.services.des.environment" -> "test",
      "microservice.services.des.authorization-token" -> "secret",
      "microservice.services.enrolment-store-proxy.host" -> wireMockHost,
      "microservice.services.enrolment-store-proxy.port" -> wireMockPort,
      "auditing.consumer.baseUri.host" -> wireMockHost,
      "auditing.consumer.baseUri.port" -> wireMockPort,
      "internal-auth-token-enabled-on-start" -> false,
      "http-verbs.retries.intervals" -> List("1ms"),
      "agent.entity.cache.enabled" -> true,
      "agent.entity.cache.expires" -> "1 second",
      "auditing.enabled" -> false,
      "rate-limiter.business-names.max-calls-per-second" -> 10
    )
//    .bindings(bind[DesConnector].toInstance(desConnector))

  val agentDetailsDesResponse = AgentDetailsDesResponse(
    Some(Utr("0123456789")),
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
    Some(SuspensionDetails(suspensionStatus = false, None)),
    Some(false)
  )

  val agentDetailsDesResponse2 = AgentDetailsDesResponse(
    Some(Utr("0123456788")),
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
    Some(SuspensionDetails(suspensionStatus = false, None)),
    Some(false)
  )

  val arn = Arn("AARN00012345")
  val arn2 = Arn("AARN00012346")

  val utr = Utr("1234567890")
  val utr2 = Utr("1234567891")
  val individualBusinessName = "First Name QM Last Name QM"
  val organisationBusinessName = "CT AGENT 165"

  "DesConnector getAgentRecord" should {
    "return agency details for a given ARN" in {
      givenDESGetAgentRecord(Arn(arn.value), Some(Utr("0123456789")))

      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse

    }
  }
  "DesConnector getAgentRecord caching check" should {
    "return agency details cached for a given ARN and save record to cache" in {
      givenDESGetAgentRecord(Arn(arn.value), Some(Utr("0123456789")))

      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse
      Thread.sleep(500)
      await(agentDataCache.getFromCache(cacheId = encryptKey(arn.value))) shouldBe Some(agentDetailsDesResponse)
      verifyDESGetAgentRecord(arn, 1)

    }

    "return agency details cached for a given ARN,  second from cache" in {
      givenDESGetAgentRecord(Arn(arn.value), Some(Utr("0123456789")))
      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse
      Thread.sleep(500)
      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse
      verifyDESGetAgentRecord(arn, 1)
    }

    "return agency details cached for a given ARN and save record to cache for two agents" in {
      givenDESGetAgentRecord(Arn(arn.value), Some(Utr("0123456789")))
      givenDESGetAgentRecord(Arn(arn2.value), Some(Utr("0123456788")))

      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse
      await(desConnector.getAgentRecord(arn2)) shouldBe agentDetailsDesResponse2
      Thread.sleep(500)
      await(agentDataCache.getFromCache(cacheId = encryptKey(arn.value))) shouldBe Some(agentDetailsDesResponse)
      await(agentDataCache.getFromCache(cacheId = encryptKey(arn2.value))) shouldBe Some(agentDetailsDesResponse2)
    }

    "return agency details cached for a given ARN,  second from cache for two agents" in {
      givenDESGetAgentRecord(Arn(arn.value), Some(Utr("0123456789")))
      givenDESGetAgentRecord(Arn(arn2.value), Some(Utr("0123456788")))
      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse
      await(desConnector.getAgentRecord(arn2)) shouldBe agentDetailsDesResponse2
      Thread.sleep(500)
      await(desConnector.getAgentRecord(arn)) shouldBe agentDetailsDesResponse
      await(desConnector.getAgentRecord(arn2)) shouldBe agentDetailsDesResponse2
      verifyDESGetAgentRecord(arn, 1)
      verifyDESGetAgentRecord(arn2, 1)
    }

    "must fail when the server returns another 5xx status" in {
      givenDesReturnsServerError()
      an[Exception] should be thrownBy await(desConnector.getAgentRecord(arn))
    }

    "must fail when the server returns agent unknown status" in {
      givenAgentIsUnknown404(Arn(arn.value))
      an[Exception] should be thrownBy await(desConnector.getAgentRecord(arn))
    }

  }


}
