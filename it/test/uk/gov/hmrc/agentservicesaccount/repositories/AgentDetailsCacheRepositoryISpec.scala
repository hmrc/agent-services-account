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

package uk.gov.hmrc.agentservicesaccount.repositories

import com.codahale.metrics.MetricRegistry
import org.mongodb.scala.ObservableFuture
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.Configuration
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, Utr}
import uk.gov.hmrc.agentservicesaccount.models.{AgencyDetails, AgentDetailsResponse, BusinessAddress, UpdateStatus}
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.crypto.SymmetricCryptoFactory.aesGcmCrypto
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class AgentDetailsCacheRepositoryISpec
  extends ComponentSpecHelper
    with Eventually
    with ScalaFutures {

  override def extraConfig: Map[String, Any] = Map(
    "agent.entity.cache.expires" -> "5 minutes",
    "agent.entity.cache.enabled" -> true
  )

  private val config: Configuration = app.injector.instanceOf[Configuration]
  private val metrics: Metrics =
    new Metrics {
      override def defaultRegistry: MetricRegistry = new MetricRegistry
    }

  private implicit val crypto: Encrypter
    & Decrypter = aesGcmCrypto("0xbYzrPV9/GmVEGazywGswm7yRYoWy2BraeJnjOUgcY=")

  private val agencyDetailsCacheRepository: AgencyDetailsCacheRepository =
    new AgencyDetailsCacheRepository(
      config = config,
      mongo = mongoComponent,
      timestampSupport = new CurrentTimestampSupport(),
      metrics = metrics
    )

  val businessAddress: BusinessAddress = BusinessAddress(
    addressLine1 = "25",
    addressLine2 = Some("Business Address Line 2"),
    addressLine3 = Some("Business Address Line 3"),
    addressLine4 = Some("Business Address Line 4"),
    postalCode = Some("GL54 1AA"),
    countryCode = "GB"
  )

  val agencyDetails: AgencyDetails = AgencyDetails(
    agencyName = Some("Agent Assurance Agency"),
    agencyEmail = Some("agencyassurance@email.com"),
    agencyTelephone = Some("01483821590"),
    agencyAddress = Some(businessAddress)
  )

  val suspensionDetails: SuspensionDetails = SuspensionDetails(
    suspensionStatus = true,
    regimes = Some(Set("ALL"))
  )

  val agencyDetailsResponse: AgentDetailsResponse = AgentDetailsResponse(
    uniqueTaxReference = Some(Utr("aa123456789")),
    agencyDetails = Some(agencyDetails),
    suspensionDetails = Some(suspensionDetails),
    isAnIndividual = Some(true),
    updateDetailsStatus = Some(UpdateStatus.ACCEPTED),
    amlSupervisionUpdateStatus = Some(UpdateStatus.REJECTED),
    directorPartnerUpdateStatus = Some(UpdateStatus.REQUIRED),
    acceptNewTermsStatus = Some(UpdateStatus.PENDING),
    reriskStatus = None
  )

  private def decryptCachedData(cacheItem: CacheItem): AgentDetailsResponse =
    (cacheItem.data \ "dataKey").validate[AgentDetailsResponse](using agencyDetailsCacheRepository.format).get


  "AgencyDetailsCacheRepository" when {
    "key does not exist in cache" should {
      "call the body and cache the result" in {


        val result = agencyDetailsCacheRepository("agent-2")(Future.successful(agencyDetailsResponse)).futureValue

        result shouldBe agencyDetailsResponse

        eventually {
          agencyDetailsCacheRepository.cacheRepo.collection.find().toFuture().futureValue.size shouldBe 1
        }

        val cachedResult = agencyDetailsCacheRepository.getFromCache("agent-2").futureValue
        cachedResult shouldBe Some(agencyDetailsResponse)

        val cacheItem: CacheItem = agencyDetailsCacheRepository.cacheRepo.
          collection.find()
          .toFuture().futureValue.head
        decryptCachedData(cacheItem) shouldBe agencyDetailsResponse
        cacheItem.id shouldBe "agent-2"
      }
    }

    "key exists in cache" should {
      "return the cached data" in {
        agencyDetailsCacheRepository.putCache(cacheId = "agent-1")(agencyDetailsResponse).futureValue

        val result: AgentDetailsResponse =
          agencyDetailsCacheRepository("agent-1")(Future.failed(throw new RuntimeException("Should not be called"))).futureValue

        result shouldBe agencyDetailsResponse

        val cacheItem: CacheItem = agencyDetailsCacheRepository.cacheRepo.collection.find().toFuture().futureValue.head
        decryptCachedData(cacheItem) shouldBe agencyDetailsResponse
        cacheItem.id shouldBe "agent-1"
      }
    }
  }

}
