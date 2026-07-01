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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, Utr}
import uk.gov.hmrc.agentservicesaccount.models.{AgencyDetails, AgentDetailsDesResponse, BusinessAddress, UpdateStatus}
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.crypto.SymmetricCryptoFactory.aesCrypto
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
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
    & Decrypter = aesCrypto("0xbYzrPV9/GmVEGazywGswm7yRYoWy2BraeJnjOUgcY=")
  private def encryptKey(key: String): String = crypto.encrypt(PlainText(key)).value
  private def decryptKey(field: String): String = crypto.decrypt(Crypted(field)).value

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

  val agencyDetailsResponse: AgentDetailsDesResponse = AgentDetailsDesResponse(
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

  private val agentDetailsEncryptedJson: JsValue = Json.parse("""
                                                                |{
                                                                |  "dataKey": {
                                                                |    "uniqueTaxReference": "dYxKUcQi7brfw2LV/jTF6Q==",
                                                                |    "agencyDetails": {
                                                                |      "agencyName": "Wdx+pMEDlSl5CvlEZi6MpDrLGPNjnx0XLGviZ3VCXKk=",
                                                                |      "agencyEmail": "HsqaxGDAUITyI08IQPDg0RxkRDlJHhe7tYcQPq70wz8=",
                                                                |      "agencyTelephone": "vxQ+sLG39lJBSyQcAmLLEg==",
                                                                |      "agencyAddress": {
                                                                |        "addressLine1": "dSa/QR2l10hGIhYZBl0nRg==",
                                                                |        "addressLine2": "vCy7qMC2hQ6E1M+TAkldhkif+6omWtQ7ge93+XqTuUQ=",
                                                                |        "addressLine3": "vCy7qMC2hQ6E1M+TAkldhkzV5LrzZyjqwSUyFZ6PcPQ=",
                                                                |        "addressLine4": "vCy7qMC2hQ6E1M+TAkldhhD6UE6fqfY3hml1fQwr/OU=",
                                                                |        "postalCode": "tnJ/JZ8+U+FQ2CWid13eHw==",
                                                                |        "countryCode": "h87BRos/A2asGKgzRou3Sg=="
                                                                |      }
                                                                |    },
                                                                |    "suspensionDetails": {
                                                                |      "suspensionStatus": true,
                                                                |      "regimes": [
                                                                |        "ALL"
                                                                |      ]
                                                                |    },
                                                                |    "isAnIndividual": true,
                                                                |    "updateDetailsStatus" : "ACCEPTED",
                                                                |    "amlSupervisionUpdateStatus" : "REJECTED",
                                                                |    "directorPartnerUpdateStatus": "REQUIRED",
                                                                |    "acceptNewTermsStatus": "PENDING",
                                                                |    "reriskStatus": null
                                                                |  }
                                                                |}
                                                                |""".stripMargin)

  private def encryptedCacheId(key: String): String = encryptKey(key)

  "AgencyDetailsCacheRepository" when {
    "key does not exist in cache" should {
      "call the body and cache the result" in {


        val result = agencyDetailsCacheRepository("agent-2")(Future.successful(agencyDetailsResponse)).futureValue

        result shouldBe agencyDetailsResponse

        eventually {
          agencyDetailsCacheRepository.cacheRepo.collection.find().toFuture().futureValue.size shouldBe 1
        }

        val cachedResult = agencyDetailsCacheRepository.getFromCache(encryptedCacheId("agent-2")).futureValue
        cachedResult shouldBe Some(agencyDetailsResponse)

        val cacheItem: CacheItem = agencyDetailsCacheRepository.cacheRepo.
          collection.find()
          .toFuture().futureValue.head
        cacheItem.data shouldBe agentDetailsEncryptedJson
        cacheItem.id shouldBe encryptedCacheId("agent-2")
        decryptKey(cacheItem.id) shouldBe "agent-2"
      }
    }

    "key exists in cache" should {
      "return the cached data" in {
        agencyDetailsCacheRepository.putCache(cacheId = encryptedCacheId("agent-1"))(agencyDetailsResponse).futureValue

        val result: AgentDetailsDesResponse =
          agencyDetailsCacheRepository("agent-1")(Future.failed(throw new RuntimeException("Should not be called"))).futureValue

        result shouldBe agencyDetailsResponse

        val cacheItem: CacheItem = agencyDetailsCacheRepository.cacheRepo.collection.find().toFuture().futureValue.head
        cacheItem.data shouldBe agentDetailsEncryptedJson
        cacheItem.id shouldBe encryptedCacheId("agent-1")
      }
    }
  }

}
