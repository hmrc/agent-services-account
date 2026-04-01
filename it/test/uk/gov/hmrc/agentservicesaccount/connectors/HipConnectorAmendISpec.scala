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
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.*
import uk.gov.hmrc.agentservicesaccount.repositories.AgencyDetailsCacheRepository
import uk.gov.hmrc.agentservicesaccount.services.CacheProvider
import uk.gov.hmrc.agentservicesaccount.stubs.HipStubs
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.crypto.SymmetricCryptoFactory.aesCrypto
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext

class HipConnectorAmendISpec
extends ComponentSpecHelper
with HipStubs {

  private given ExecutionContext = ExecutionContext.global
  private given Request[AnyContentAsEmpty.type] = FakeRequest()

  override def extraConfig: Map[String, Any] = Map(
    "microservice.services.hip.host" -> mockHost,
    "microservice.services.hip.port" -> mockPort,
    "microservice.services.hip.authorization-token" -> "secret",
    "http-verbs.retries.intervals" -> List("1ms"),
    "agent.entity.cache.enabled" -> true,
    "agent.entity.cache.expires" -> "1 second",
    "auditing.enabled" -> false
  )

  given configuration: Config = app.injector.instanceOf[Config]
  given as: ActorSystem = ActorSystem()

  private given (Encrypter & Decrypter) =
    aesCrypto("0xbYzrPV9/GmVEGazywGswm7yRYoWy2BraeJnjOUgcY=")

  lazy val agentDataCache =
    AgencyDetailsCacheRepository(
      app.injector.instanceOf[Configuration],
      mongoComponent,
      CurrentTimestampSupport(),
      app.injector.instanceOf[Metrics]
    )

  lazy val cacheProvider = CacheProvider(agentDataCache, app.injector.instanceOf[Configuration])

  lazy val hipConnector =
    HipConnector(
      app.injector.instanceOf[AppConfig],
      app.injector.instanceOf[HttpClientV2],
      cacheProvider,
      configuration,
      as
    )

  val arn = Arn("AARN00012345")

  val amlsPayload = HipAmendPayload(
    supervisoryBody = Some("SRA"),
    membershipNumber = Some("XAML00000123456"),
    amlSupervisionUpdateStatus = Some(UpdateStatus.ACCEPTED)
  )

  val agencyPayload = HipAmendPayload(
    name = Some("Test Agency"),
    addr1 = Some("1 High Street"),
    country = Some("GB"),
    email = Some("test@example.com"),
    updateDetailsStatus = Some(UpdateStatus.ACCEPTED)
  )

  "HipConnector putAgentRecord" should {

    "return HipAmendResponse for AMLS update" in {
      givenHipAmendAgentRecordSuccess(arn)

      val result = hipConnector.putAgentRecord(arn, amlsPayload).futureValue

      result.success.processingDate shouldBe "2024-07-15T09:30:47Z"
      verifyHipAmendAgentRecord(arn, 1)
    }

    "return HipAmendResponse for agency details update" in {
      givenHipAmendAgentRecordSuccess(arn)

      val result = hipConnector.putAgentRecord(arn, agencyPayload).futureValue

      result.success.processingDate shouldBe "2024-07-15T09:30:47Z"
    }

    "invalidate the cache after successful PUT" in {
      givenHIPGetAgentRecordSuspendedAgent(arn)
      givenHipAmendAgentRecordSuccess(arn)

      // populate the cache via GET
      hipConnector.getAgentRecord(arn).futureValue
      Thread.sleep(500)

      // PUT should invalidate the cache
      hipConnector.putAgentRecord(arn, amlsPayload).futureValue
      Thread.sleep(500)

      // second GET should hit HIP again (not cache)
      hipConnector.getAgentRecord(arn).futureValue
      verifyHipGetAgentRecord(arn, 2)
    }

    "fail when HIP returns 500" in {
      givenHipAmendAgentRecordError(arn, 500)
      an[Exception] should be thrownBy await(hipConnector.putAgentRecord(arn, amlsPayload))
    }

    "fail when HIP returns 404" in {
      givenHipAmendAgentRecordError(arn, 404)
      an[Exception] should be thrownBy await(hipConnector.putAgentRecord(arn, amlsPayload))
    }

    "fail when HIP returns 422" in {
      givenHipAmendAgentRecordError(arn, 422)
      an[Exception] should be thrownBy await(hipConnector.putAgentRecord(arn, amlsPayload))
    }
  }
}
