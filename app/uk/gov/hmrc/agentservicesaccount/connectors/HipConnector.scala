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
import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.AgencyDetails
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.models.BusinessAddress
import uk.gov.hmrc.agentservicesaccount.models.HipAgentSubscriptionDisplayResponse
import uk.gov.hmrc.agentservicesaccount.services.CacheProvider
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class HipConnector @Inject() (
  appConfig: AppConfig,
  httpV2: HttpClientV2,
  agentCacheProvider: CacheProvider,
  override val configuration: Config,
  override val actorSystem: ActorSystem
)(using ec: ExecutionContext)
extends BaseConnector
with Logging {

  private val baseUrl = appConfig.hipBaseUrl
  private val authToken = appConfig.hipAuthToken
  private val originatingSystem = "MDTP-ASA"
  private val transmittingSystem = "HIP"

  def getAgentRecord(arn: Arn)(using request: RequestHeader): Future[AgentDetailsDesResponse] = {

    val url = url"$baseUrl/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"

    agentCacheProvider.agentDetailsCache(arn.value) {
      getWithHipHeadersWithRetry(url)
        .map(mapHipToDesModel)
    }
  }

  private def getWithHipHeadersWithRetry(
    url: URL
  )(using request: RequestHeader): Future[HipAgentSubscriptionDisplayResponse] = {

    retryFor[HipAgentSubscriptionDisplayResponse](s"HIP get $url")(retryCondition) {
      httpV2
        .get(url)
        .setHeader(hipHeaders*)
        .executeAndDeserialise[HipAgentSubscriptionDisplayResponse]
    }
  }

  private def hipHeaders(using request: RequestHeader): Seq[(String, String)] = {
    Seq(
      "Authorization" -> s"Basic $authToken",
      "correlationid" -> UUID.randomUUID().toString,
      "X-Originating-System" -> originatingSystem,
      "X-Receipt-Date" -> java.time.Instant.now().toString,
      "X-Transmitting-System" -> transmittingSystem
    )
  }

  private def mapHipToDesModel(
    hipResponse: HipAgentSubscriptionDisplayResponse
  ): AgentDetailsDesResponse = {

    val s = hipResponse.AgentSubscriptionDisplay_Response.success
    val suspension = SuspensionDetails(
      suspensionStatus = s.suspensionStatus == "T",
      regimes = s.regime.filter(_.nonEmpty).map(_.toSet)
    )
    AgentDetailsDesResponse(
      uniqueTaxReference = s.utr.map(Utr(_)),
      agencyDetails = Some(
        AgencyDetails(
          agencyName = Some(s.name),
          agencyEmail = Some(s.email),
          agencyTelephone = s.phone,
          agencyAddress = Some(
            BusinessAddress(
              addressLine1 = s.addr1,
              addressLine2 = s.addr2,
              addressLine3 = s.addr3,
              addressLine4 = s.addr4,
              postalCode = s.postcode,
              countryCode = s.country
            )
          )
        )
      ),
      suspensionDetails = Some(suspension),
      isAnIndividual = Some(true)
    )
  }

}
