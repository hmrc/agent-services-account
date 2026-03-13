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

import play.api.http.Status.OK
import play.api.libs.json.JsObject
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.RoboticsIds.CorrelationId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
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
extends Logging:

  private val baseUrl: String = appConfig.hipBaseUrl

  def invoke(
    payload: JsObject,
    correlationId: CorrelationId
  )(using HeaderCarrier): Future[Unit] = http
    .post(url"$baseUrl/RTServer/rest/nice/rti/ra/invocation")
    .setHeader("correlationId" -> correlationId.value)
    .withBody(payload)
    .execute[HttpResponse]
    .map { response =>
      response.status match {
        case status if status / 100 == 2 =>
          // Spec and stubs currently return 200. Guard against HIP/proxy layers returning other 2xx (e.g. 202/204),
          // because treating those as errors would cause unnecessary retries and potentially duplicate submissions.
          if status != OK then
            logger.warn(s"[RoboticsInvocationConnector][invoke] Received $status from robotics invocation endpoint (correlationId=${correlationId.value})")
          ()
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
