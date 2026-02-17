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
import play.api.libs.json.Reads
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.{CredId, Es20Request, Es20Response, Es8Request, EspKnownFact, GroupId}
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*

@Singleton
class EnrolmentStoreProxyConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(using
  ec: ExecutionContext
):

  private val baseUrl: String = appConfig.enrolmentStoreProxyBaseUrl

  /*
   * ES3: Query Enrolments allocated to a group
   * https://confluence.tools.tax.service.gov.uk/display/GGWRLS/ES3+-+Query+Enrolments+allocated+to+a+group
   */
  def queryEnrolmentsAllocatedToGroup(
    groupId: GroupId
  )(using
    request: RequestHeader
  ): Future[List[EnrolmentStoreProxyConnector.Enrolment]] = {
    val url = url"$baseUrl/enrolment-store-proxy/enrolment-store/groups/${groupId.value}/enrolments?type=principal"
    http
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK => (response.json \ "enrolments").as[List[EnrolmentStoreProxyConnector.Enrolment]]
          case NO_CONTENT => Nil
          case other =>
            throw UpstreamErrorResponse(
              response.body,
              other,
              other
            )
        }
      }
  }

  def queryKnownFactsForAgent(regime: LegacyRegime, agentReference: String)(using HeaderCarrier): Future[Option[Es20Response]] = {
    val request = Es20Request(
      service = regime.enrolmentKey,
      knownFacts = Seq(EspKnownFact(regime.agentReferenceKey, agentReference))
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

  def allocateAgentEnrolment(
    regime: LegacyRegime,
    groupId: GroupId,
    agentReference: String,
    adminCredId: CredId
  )(using HeaderCarrier): Future[Unit] = {
    val enrolmentKey = s"${regime.enrolmentKey}~${regime.agentReferenceKey}~$agentReference"

    http
      .post(url"$baseUrl/enrolment-store-proxy/enrolment-store/groups/${groupId.value}/enrolments/$enrolmentKey")
      .withBody(Json.toJson(Es8Request(adminCredId.value, "principal", "enrolAndActivate")))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => ()
          case status =>
            throw UpstreamErrorResponse(response.body, status, status)
        }
      }
  }

object EnrolmentStoreProxyConnector:

  final case class Enrolment(
    service: String,
    state: String
  )

  object Enrolment:
    given Reads[Enrolment] = Json.reads[Enrolment]
