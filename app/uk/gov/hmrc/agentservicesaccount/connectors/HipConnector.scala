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
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR, UNPROCESSABLE_ENTITY}
import play.api.mvc.RequestHeader
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.helpers.CommonHeaders
import uk.gov.hmrc.agentservicesaccount.models.{AgentDetailsResponse, DesRegistrationOrganisation, DesRegistrationRequest, DesRegistrationResponse, HipAgentSubscriptionResponse, HipAmendPayload, HipAmendResponse}
import uk.gov.hmrc.agentservicesaccount.models.HipAmendPayload.given
import uk.gov.hmrc.agentservicesaccount.services.CacheProvider
import uk.gov.hmrc.agentservicesaccount.utils.RequestAwareLogging
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.http.{BadRequestException, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

import java.net.URL
import java.time.temporal.ChronoUnit.SECONDS
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

@Singleton
class HipConnector @Inject() (
  appConfig: AppConfig,
  httpV2: HttpClientV2,
  agentCacheProvider: CacheProvider,
  override val configuration: Config,
  override val actorSystem: ActorSystem
)(using ec: ExecutionContext)
extends BaseConnector
with RequestAwareLogging {

  private val baseUrl = appConfig.hipBaseUrl
  private val authToken = appConfig.hipAuthToken
  private val originatingSystem = "MDTP-ASA"
  private val transmittingSystem = "HIP"

  def getAgentRecord(arn: Arn)(using request: RequestHeader): Future[AgentDetailsResponse] = {

    val url = url"$baseUrl/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"

    agentCacheProvider.agentDetailsCache(arn.value) {
      getWithHipHeadersWithRetry(url)
        .map(_.success.toAgentDetailsResponse)
    }
  }

  private def getWithHipHeadersWithRetry(
    url: URL
  )(using request: RequestHeader): Future[HipAgentSubscriptionResponse] = {

    retryFor[HipAgentSubscriptionResponse](s"HIP get $url")(retryCondition) {
      httpV2
        .get(url)
        .setHeader(hipHeaders*)
        .executeAndDeserialise[HipAgentSubscriptionResponse]
    }
  }

  private def hipHeaders(using request: RequestHeader): Seq[(String, String)] = {
    CommonHeaders() ++ Seq(
      "Authorization" -> s"Basic $authToken",
      "correlationid" -> UUID.randomUUID().toString,
      "X-Originating-System" -> originatingSystem,
      "X-Receipt-Date" -> java.time.Instant.now().truncatedTo(SECONDS).toString,
      "X-Transmitting-System" -> transmittingSystem
    )
  }

  def putAgentRecord(
    arn: Arn,
    payload: HipAmendPayload
  )(using request: RequestHeader): Future[HipAmendResponse] =
    val url = url"$baseUrl/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"
    retryFor[HipAmendResponse](s"HIP put $url")(retryCondition) {
      httpV2
        .put(url)
        .withBody(Json.toJson(payload))
        .setHeader(hipHeaders*)
        .executeAndDeserialise[HipAmendResponse]
    }.flatMap { response =>
      agentCacheProvider.agentDetailsCache.delete(key = arn.value)
        .recover { case e => logger.warn(s"Failed to invalidate agent details cache: ${e.getMessage}") }
        .map(_ => response)
    }

  def getRegistration(utr: Utr)(implicit rh: RequestHeader): Future[Option[DesRegistrationResponse]] =
    getRegistrationJson(utr).map {
      case Some(r) =>
        val innerJson = (r \ "success").as[JsObject]
        Some(
          DesRegistrationResponse(
            isAnIndividual = (innerJson \ "isAnIndividual").as[Boolean],
            organisation = (innerJson \ "organisation" \ "organisationType").asOpt[String]
              .map(ot => DesRegistrationOrganisation(Some(ot)))
          )
        )
      case _ => None
    }

  private def getRegistrationJson(utr: Utr)(implicit rh: RequestHeader): Future[Option[JsValue]] =
    val url: URL = url"$baseUrl/etmp/RESTAdapter/registration/UTR/${utr.value}"
    httpV2
      .post(url)
      .setHeader(hipHeaders*)
      .withBody(Json.toJson(DesRegistrationRequest()))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => Some(response.json)
          case UNPROCESSABLE_ENTITY if isNoMatchFound(response.body) => None
          case error =>
            throw UpstreamErrorResponse(
              s"[HIP-GetAgentRegistration-POST] returned status: $error",
              INTERNAL_SERVER_ERROR
            )
        }
      }
      .recover { case badRequest: BadRequestException => throw new Exception(s"400 Bad Request response from HIP for utr ${utr.value}", badRequest) }

  private def isNoMatchFound(body: String): Boolean = {
    Try(Json.parse(body))
      .toOption
      .flatMap(json => (json \ "errors" \ "code").asOpt[String])
      .contains("002")
  }
}
