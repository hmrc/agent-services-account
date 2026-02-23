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

import play.api.Logging
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.MappingConnector.Mapping.reads
import uk.gov.hmrc.agentservicesaccount.connectors.MappingConnector.Mapping
import uk.gov.hmrc.agentservicesaccount.connectors.MappingConnector.Mappings
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentMappingConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(implicit
  val ec: ExecutionContext
)
extends Logging:

  private val baseUrl = s"${appConfig.agentMappingBaseUrl}/agent-mapping"

  def getMappings(
    arn: Arn,
    regime: LegacyRegime
  )(implicit rh: RequestHeader): Future[Seq[Mapping]] = httpClient
    .get(url"$baseUrl/mappings/key/${regime.mappingKey}/arn/${arn.value}")
    .execute[HttpResponse]
    .map { response =>
      response.status match {
        case Status.OK => response.json.as[Mappings].mappings
        case Status.NOT_FOUND => Nil
        case other =>
          throw UpstreamErrorResponse(
            response.body,
            other,
            other
          )
      }
    }

  def performAutoMapping(arn: Arn)(using rh: RequestHeader): Future[Unit] = httpClient
    .put(url"${appConfig.agentMappingBaseUrl}/agent-mapping/mappings/auto-map/arn/${arn.value}")
    .execute[HttpResponse]
    .map(_ => ())
    .recover { case _ => () }

object MappingConnector:

  case class Mappings(mappings: Seq[Mapping])
  case class Mapping(
    arn: Arn,
    identifier: AgentReference
  )

  object Mapping:

    implicit val mappingReads: Reads[Mapping] = Json.reads[Mapping]
    implicit val reads: Reads[Mappings] = Json.reads[Mappings]
