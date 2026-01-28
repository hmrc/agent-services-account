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

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given 
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation.given 
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue



@Singleton
class EmailConnector @Inject() (
  appConfig: AppConfig,
  httpClient: HttpClientV2
)(using val ec: ExecutionContext)
extends HttpErrorFunctions
with Logging {

  def sendEmail(emailInformation: EmailInformation)(using request: RequestHeader): Future[Unit] = {
    httpClient
      .post(url"${appConfig.emailBaseUrl}/hmrc/email")(using hc)
      .withBody(Json.toJson(emailInformation))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case status if is2xx(status) => ()
          case other =>
            logger.error(s"unexpected status from email service, status: $other")
            ()
        }
      }

  }

}
