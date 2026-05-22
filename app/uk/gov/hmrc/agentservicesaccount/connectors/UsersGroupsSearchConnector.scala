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
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

final case class UserDetails(
  userId: Option[String] = None,
  credentialRole: Option[String] = None
)

object UserDetails:
  given OFormat[UserDetails] = Json.format[UserDetails]

@Singleton
class UsersGroupsSearchConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(using ec: ExecutionContext)
extends Logging:

  private val baseUrl: String = appConfig.usersGroupsSearchBaseUrl

  def getFirstAdminCredId(groupId: GroupId)(using HeaderCarrier): Future[Option[CredId]] = http
    .get(url"$baseUrl/users-groups-search/groups/${groupId.value}/users")
    .execute[HttpResponse]
    .map { response =>
      response.status match
        case OK =>
          response.json
            .as[Seq[UserDetails]]
            .collectFirst {
              case UserDetails(Some(userId), Some(role)) if role == "Admin" || role == "User" => userId
            }
            .map(CredId.apply)
        case NOT_FOUND =>
          logger.warn(s"[UsersGroupsSearchConnector] Group not found in users-groups-search: ${groupId.value}")
          None
        case status =>
          throw UpstreamErrorResponse(
            response.body,
            status,
            status
          )
    }
