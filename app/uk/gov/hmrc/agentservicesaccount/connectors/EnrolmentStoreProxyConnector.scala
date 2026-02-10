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

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.{Es20Request, Es20Response, Es8Request, EspKnownFact}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*
@Singleton
class EnrolmentStoreProxyConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(
  implicit ec: ExecutionContext
):

  private val baseUrl: String = appConfig.enrolmentStoreProxyBaseUrl

  def queryKnownFactsForPayeAgent(payeAgentRef: String)(using HeaderCarrier): Future[Option[Es20Response]] = {
    val request = Es20Request(
      service = "IR-PAYE-AGENT",
      knownFacts = Seq(EspKnownFact("IRAgentReference", payeAgentRef))
    )

    http
      .post(url"$baseUrl/enrolment-store-proxy/enrolment-store/enrolments")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => Some(response.json.as[Es20Response])
          case NO_CONTENT => None
          case status =>
            throw UpstreamErrorResponse(response.body, status, status)
        }
      }
  }

  def allocatePayeAgentEnrolment(
    groupId: String,
    payeAgentRef: String,
    adminCredId: String
  )(using HeaderCarrier): Future[Unit] = {
    val enrolmentKey = s"IR-PAYE-AGENT~IRAgentReference~$payeAgentRef"

    http
      .post(url"$baseUrl/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments/$enrolmentKey")
      .withBody(Json.toJson(Es8Request(adminCredId, "principal")))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => ()
          case status =>
            throw UpstreamErrorResponse(response.body, status, status)
        }
      }
  }
