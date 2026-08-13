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

package uk.gov.hmrc.agentservicesaccount.services

import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.connectors.DesConnector
import uk.gov.hmrc.agentservicesaccount.connectors.HipConnector
import uk.gov.hmrc.agentservicesaccount.models.DesRegistrationResponse
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentEntityType

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class AgentEntityTypeService @Inject() (
  desConnector: DesConnector,
  hipConnector: HipConnector
)(using ec: ExecutionContext)
extends Logging:

  def resolve(arn: Arn)(using request: RequestHeader): Future[String] =
    hipConnector.getAgentRecord(arn)
      .flatMap { record =>
        record.uniqueTaxReference match
          case None => Future.successful(AgentEntityType.Overseas)
          case Some(utr) =>
            desConnector
              .getRegistration(utr)
              .map(_.map(toEntityType).getOrElse(AgentEntityType.Unknown))
      }
      .recover { case NonFatal(error) =>
        logger.warn(s"[AgentEntityTypeService] Failed to resolve entity type for ARN ${arn.value}", error)
        AgentEntityType.Unknown
      }

  private def toEntityType(response: DesRegistrationResponse): String =
    if response.isAnIndividual then
      AgentEntityType.SoleTrader
    else
      AgentEntityType.fromOrganisationType(response.organisation.flatMap(_.organisationType))
