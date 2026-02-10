/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*

@Singleton
class AgentEpayeRegistrationConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(
  implicit ec: ExecutionContext
):

  val baseUrl: String = appConfig.agentEpayeRegistrationBaseUrl

  def register(subscriptionRequest: PayeSubscriptionRequest)(using request: RequestHeader): Future[AgentReference] = {
    val url = url"$baseUrl/agent-epaye-registration/registrations"

    http
      .post(url)
      .withBody(Json.toJson(subscriptionRequest)(PayeSubscriptionRequest.registerWrites))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK =>
            val json = response.json
            (json \ "agentReference")
              .asOpt[AgentReference]
              .orElse((json \ "payeAgentReference").asOpt[AgentReference])
              .getOrElse(
                throw UpstreamErrorResponse(
                  s"Agent reference missing in Agent Epaye Registration response: ${response.body}",
                  response.status
                )
              )
          case status =>
            throw UpstreamErrorResponse(
              s"Unexpected response from Agent Epaye Registration: ${response.body}",
              status
            )
        }
      }
  }
