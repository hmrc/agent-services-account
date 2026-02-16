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

import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.*
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentAssuranceConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(implicit val ec: ExecutionContext) {

  val baseUrl = appConfig.agentAssuranceBaseUrl

  def getAgentUtrChecks(utr: Utr)(using request: RequestHeader): Future[UtrChecksResponse] = {
    val url = url"$baseUrl/agent-assurance/restricted-collection-check/utr/${utr.value}?nameRequired=false"
    http
      .get(url)
      .execute[UtrChecksResponse]
  }

}
