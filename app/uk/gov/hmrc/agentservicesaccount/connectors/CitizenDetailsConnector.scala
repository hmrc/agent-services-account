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

import play.api.Logging
import play.api.http.Status
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class CitizenDeceased(deceased: Boolean)

object CitizenDeceased {
  implicit val reads: Reads[CitizenDeceased] = (JsPath \ "deceased")
    .readNullable[Boolean]
    .map(x => CitizenDeceased(x.getOrElse(false)))
}

@Singleton
class CitizenDetailsConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(implicit ec: ExecutionContext)
extends Logging {

  private val baseUrl = appConfig.citizenDetailsBaseUrl

  def getCitizenDeceasedFlag(
    saUtr: SaUtr
  )(using request: RequestHeader): Future[Option[DeceasedCheckException]] = {
    http
      .get(url"$baseUrl/citizen-details/sautr/${saUtr.value}")
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case Status.OK =>
            Json.parse(response.body).as[CitizenDeceased] match {
              case x: CitizenDeceased if !x.deceased => None
              case _ => Some(DeceasedCheckException.EntityDeceasedCheckFailed)
            }
          case e => Some(DeceasedCheckException.CitizenConnectorRequestFailed(e))
        }
      }

  }

}
