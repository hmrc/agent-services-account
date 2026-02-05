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

package uk.gov.hmrc.agentservicesaccount.services

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.apache.pekko.Done
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.AgentEpayeRegistrationConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport

@Singleton
class SubscriptionService @Inject() (
  agentEpayeRegistrationConnector: AgentEpayeRegistrationConnector,
  subscriptionWorkItemRepository: SubscriptionWorkItemRepository,
  appConfig: AppConfig
)(implicit ec: ExecutionContext):

  def startPayeSubscription(
    arn: Arn,
    subscriptionRequest: PayeSubscriptionRequest
  )(using request: RequestHeader): Future[Done] = agentEpayeRegistrationConnector.register(subscriptionRequest).flatMap { agentReference =>
    lazy val optSessionId: Option[String] =
      if (appConfig.stubsCompatibilityMode)
        RequestSupport.hc.sessionId.map(_.value)
      else
        None // only required for local testing against stubs

    subscriptionWorkItemRepository
      .pushNew(
        SubscriptionWorkItem(
          arn = arn,
          subscriptionRequest = subscriptionRequest,
          regime = PAYE,
          agentReference = Some(agentReference),
          sessionId = optSessionId
        )
      )
      .map(_ => Done)
  }

  def handleRoboticsCallback(callback: SubscriptionCallback, correlationId: String): Future[Boolean] =
    subscriptionWorkItemRepository.addAgentReference(callback.agentId, correlationId)
