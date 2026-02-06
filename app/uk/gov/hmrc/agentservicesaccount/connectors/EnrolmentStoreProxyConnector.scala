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
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.Reads
import play.api.libs.json.__
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import uk.gov.hmrc.http.HttpReads.Implicits._

@Singleton
class EnrolmentStoreProxyConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(using
  ec: ExecutionContext
)
extends Logging:

  private val baseUrl: String = s"${appConfig.enrolmentStoreProxyBaseUrl}/enrolment-store-proxy"

  /*
   * ES3: Query Enrolments allocated to a group
   * https://confluence.tools.tax.service.gov.uk/display/GGWRLS/ES3+-+Query+Enrolments+allocated+to+a+group
   */
  def queryEnrolmentsAllocatedToGroup(
    groupId: GroupId
  )(using
    request: RequestHeader
  ): Future[List[EnrolmentStoreProxyConnector.Enrolment]] = {
    val url = url"$baseUrl/enrolment-store/groups/$groupId/enrolments?type=principal"
    httpClient
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case Status.OK => response.json.as[List[EnrolmentStoreProxyConnector.Enrolment]]
          case Status.NO_CONTENT => Nil
          case other =>
            throw UpstreamErrorResponse(
              response.body,
              other,
              other
            )
        }
      }
  }

object EnrolmentStoreProxyConnector:

  final case class Enrolment(
    service: String,
    state: String
  )

  object Enrolment:
    given Reads[Enrolment] =
      (
        (__ \ "service").read[String] and
        (__ \ "state").read[String]
      )(Enrolment.apply)
