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
import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.AgentEpayeRegistrationConnector
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.connectors.AgentMappingConnector
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackFailure
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackSuccess
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionStatus.NotSubscribed
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionStatus.SubscriptionMapped
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionStatus.SubscriptionOnAgency
import uk.gov.hmrc.agentservicesaccount.repositories.SubscriptionWorkItemRepository
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport

@Singleton
class SubscriptionService @Inject() (
  agentEpayeRegistrationConnector: AgentEpayeRegistrationConnector,
  subscriptionWorkItemRepository: SubscriptionWorkItemRepository,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  agentMappingConnector: AgentMappingConnector,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
extends Logging:

  def startPayeSubscription(
    arn: Arn,
    subscriptionRequest: PayeSubscriptionRequest,
    adminCredId: String,
    groupId: String
  )(using request: RequestHeader): Future[Done] = agentEpayeRegistrationConnector.register(subscriptionRequest).flatMap { agentReference =>
    // Local stub-only: ESP stubs require session + bearer; never persist in QA/Prod.
    val (optSessionId, optBearerToken) =
      if (appConfig.stubsCompatibilityMode)
        (RequestSupport.hc.sessionId.map(_.value), RequestSupport.hc.authorization.map(_.value))
      else
        (None, None)

    subscriptionWorkItemRepository
      .pushNew(
        SubscriptionWorkItem(
          arn = arn,
          subscriptionRequest = subscriptionRequest,
          regime = PAYE,
          agentReference = Some(agentReference),
          groupId = Some(groupId),
          adminCredId = Some(adminCredId),
          sessionId = optSessionId,
          bearerToken = optBearerToken
        )
      )
      .map(_ => Done)
  }

  def handleRoboticsCallback(
    callback: SubscriptionCallback,
    correlationId: String
  ): Future[Boolean] =
    callback.status match {
      case CallbackSuccess => subscriptionWorkItemRepository.addAgentReference(callback.agentId, correlationId)
      case CallbackFailure =>
        logger.error(s"[handleRoboticsCallback] Robotics callback for correlationId $correlationId returned failed status, reason: '${callback.requestMessage}', marking work item as permanently failed")
        subscriptionWorkItemRepository.markAsPermanentlyFailed(correlationId)
    }

  def getSubscriptionInfo(
    arn: Arn,
    groupId: GroupId,
    regimes: Seq[LegacyRegime]
  )(implicit requestHeader: RequestHeader): Future[Seq[SubscriptionInfo]] = Future.sequence(regimes.map { regime =>
    subscriptionWorkItemRepository.findByArnAndRegime(arn, regime).flatMap {
      case Some(workItem) =>
        Future.successful(
          SubscriptionInfo(
            regime = regime,
            subscriptionStatus = SubscriptionStatus.fromProcessingStatus(workItem.status)
          )
        )
      case None =>
        enrolmentStoreProxyConnector.queryEnrolmentsAllocatedToGroup(groupId).flatMap {
          case enrolments if enrolments.exists(e => e.service == regime.enrolmentKey && e.state == "Activated") =>
            Future.successful(
              SubscriptionInfo(
                regime = regime,
                subscriptionStatus = SubscriptionOnAgency
              )
            )
          case _ =>
            agentMappingConnector.getMappings(arn, regime).map {
              case mappings if mappings.nonEmpty =>
                SubscriptionInfo(
                  regime = regime,
                  subscriptionStatus = SubscriptionMapped
                )
              case _ =>
                SubscriptionInfo(
                  regime = regime,
                  subscriptionStatus = NotSubscribed
                )
            }
        }
    }
  })
