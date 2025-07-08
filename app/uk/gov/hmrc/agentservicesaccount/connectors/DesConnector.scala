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
import play.api.Logging
import play.api.libs.json.*
import play.api.libs.json.Reads.*
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.services.CacheProvider
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderNames, HttpReads}

import java.net.URL
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class ClientRelationship(agents: Seq[Agent])

case class Agent(
  agentId: Option[SaAgentReference],
  hasAgent: Boolean,
  agentCeasedDate: Option[String]
)

object ClientRelationship {

  given agentReads: Reads[Agent] = Json.reads[Agent]

  given readClientRelationship: Reads[ClientRelationship] = (JsPath \ "agents")
    .readNullable[Seq[Agent]]
    .map(optionalAgents => ClientRelationship(optionalAgents.getOrElse(Seq.empty)))

}

case class RegistrationRelationshipResponse(processingDate: String)

object RegistrationRelationshipResponse {
  given reads: Reads[RegistrationRelationshipResponse] = Json.reads[RegistrationRelationshipResponse]
}


@Singleton
class DesConnector @Inject() (
  appConfig: AppConfig,
  httpV2: HttpClientV2,
  agentCacheProvider: CacheProvider,
  override val configuration: Config,
  override val actorSystem: ActorSystem
)(using ec: ExecutionContext)
extends BaseConnector
with Logging {

  private val baseUrl = appConfig.desBaseUrl
  private val authorizationToken = appConfig.desAuthToken
  private val environment = appConfig.desEnv

  private val Environment = "Environment"
  private val CorrelationId = "CorrelationId"
  
  // API #1170 (API#4) Get Agent Record
  def getAgentRecord(arn: Arn)(using request: RequestHeader): Future[AgentDetailsDesResponse] = {
    val url = new URL(s"$baseUrl/registration/personal-details/arn/${arn.value}")
    agentCacheProvider.agentDetailsCache(arn.value) {
      getWithDesHeadersWithRetry[AgentDetailsDesResponse]("GetAgentRecordCached", url)
    }
  }

  private def getWithDesHeadersWithRetry[A: HttpReads](
    apiName: String,
    url: URL
  )(using request: RequestHeader,
    x: Reads[A]
  ): Future[A] = {

    val isInternalHost = appConfig.internalHostPatterns.exists(_.pattern.matcher(url.getHost).matches())

    retryFor[A](s"$apiName connector get $url")(retryCondition) {
      httpV2
        .get(url)
        .setHeader(desHeaders(
          authorizationToken,
          environment,
          isInternalHost
        ): _*)
        .executeAndDeserialise[A]
    }
  }

  /*
   * If the service being called is external (e.g. DES/IF in QA or Prod):
   * headers from HeaderCarrier are removed (except user-agent header).
   * Therefore, required headers must be explicitly set.
   * See https://github.com/hmrc/http-verbs?tab=readme-ov-file#propagation-of-headers
   * */

  private def desHeaders(
    authToken: String,
    env: String,
    isInternalHost: Boolean
  )(using request: RequestHeader): Seq[(String, String)] = {

    val additionalHeaders =
      if (isInternalHost)
        Seq.empty
      else
        Seq(
          HeaderNames.authorisation -> s"Bearer $authToken",
          HeaderNames.xRequestId -> myHc.requestId.map(_.value).getOrElse(UUID.randomUUID().toString)
        ) ++ myHc.sessionId.fold(Seq.empty[(String, String)])(x => Seq(HeaderNames.xSessionId -> x.value))
    val commonHeaders = Seq(Environment -> env, CorrelationId -> UUID.randomUUID().toString)
    commonHeaders ++ additionalHeaders
  }

}
