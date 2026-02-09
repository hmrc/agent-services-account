/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime

trait AgentMappingStubs:

  def givenGetMappingsCallSucceeds(
    arn: Arn,
    regime: LegacyRegime
  )(agentReferences: Seq[AgentReference]): Unit = stubFor(
    get(urlEqualTo(s"/agent-mapping/mappings/key/${regime.mappingKey}/arn/${arn.value}"))
      .willReturn(
        aResponse()
          .withStatus(if (agentReferences.isEmpty)
            404
          else
            200)
          .withBody(Json.obj(
            "mappings" -> Json.arr(
              agentReferences.map { reference =>
                Json.obj(
                  "arn" -> arn.value,
                  "identifier" -> reference
                )
              }*
            )
          ).toString())
      )
  )

  def givenGetMappingsCallFails(
    arn: Arn,
    regime: LegacyRegime
  ): Unit = stubFor(
    get(urlEqualTo(s"/agent-mapping/mappings/key/${regime.mappingKey}/arn/${arn.value}"))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
  )
