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

import org.apache.pekko.Done
import play.api.libs.json.JsObject
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class RoboticsInvocationConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(using
  ec: ExecutionContext
)
extends Logging
with HttpErrorFunctions:

  private val baseUrl = appConfig.hipBaseUrl
  private val authToken = appConfig.hipAuthToken

  def invoke(
    payload: JsObject,
    correlationId: CorrelationId
  )(using HeaderCarrier): Future[Done] = {
    val roboticsURL = if appConfig.stubsCompatibilityMode then
      s"$baseUrl/RTServer/rest/nice/rti/ra/invocation"
    else
      s"$baseUrl/customer-management-and-engagement/automation/invocations"
    end roboticsURL

    http
      .post(url"$roboticsURL")
      .setHeader("correlationId" -> correlationId.value)
      .setHeader("Authorization" -> s"Basic $authToken")
      .withBody(payload)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case status if is2xx(status) => Done
          case status =>
            // Do not include the outbound payload in exception messages; this may be logged by callers and could
            // contain PII (e.g. postcode). Use correlationId for traceability instead.
            throw UpstreamErrorResponse(
              s"Unexpected response from robotics invocation endpoint (status=$status, correlationId=${correlationId.value})",
              status,
              status
            )
        }
      }
  }
