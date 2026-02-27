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

import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoCommandException
import com.mongodb.MongoWriteException
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
import uk.gov.hmrc.agentservicesaccount.models.CredId
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
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.PermanentlyFailed

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

@Singleton
class SubscriptionService @Inject() (
  agentEpayeRegistrationConnector: AgentEpayeRegistrationConnector,
  subscriptionWorkItemRepository: SubscriptionWorkItemRepository,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  agentMappingConnector: AgentMappingConnector,
  appConfig: AppConfig
)(using ec: ExecutionContext)
extends Logging:

  private def maybeCaptureStubHeaders()(using request: RequestHeader): (Option[String], Option[String]) =
    if (appConfig.stubsCompatibilityMode)
      (RequestSupport.hc.sessionId.map(_.value), RequestSupport.hc.authorization.map(_.value))
    else
      (None, None)

  private def isDuplicateKeyException(error: Throwable): Boolean =
    error match
      case e: MongoWriteException => e.getError.getCode == 11000 || e.getError.getMessage.contains("E11000")
      case e: MongoBulkWriteException =>
        e.getWriteErrors.asScala.exists(we => we.getCode == 11000 || we.getMessage.contains("E11000"))
      case e: MongoCommandException => e.getErrorCode == 11000 || e.getErrorMessage.contains("E11000")
      case e => Option(e.getMessage).exists(_.contains("E11000"))

  def startPayeSubscription(
    arn: Arn,
    subscriptionRequest: PayeSubscriptionRequest,
    adminCredId: CredId,
    groupId: GroupId
  )(using request: RequestHeader): Future[Done] = agentEpayeRegistrationConnector.register(subscriptionRequest).flatMap { agentReference =>
    // Local stub-only: ESP stubs require session + bearer; never persist in QA/Prod.
    val (optSessionId, optBearerToken) = maybeCaptureStubHeaders()

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

  def startSaSubscription(
    arn: Arn,
    subscriptionRequest: SaSubscriptionRequest,
    adminCredId: CredId,
    groupId: GroupId
  )(using request: RequestHeader): Future[Done] =
    enrolmentStoreProxyConnector.queryEnrolmentsAllocatedToGroup(groupId).flatMap { enrolments =>
      val alreadyEnrolled = enrolments.exists(e => e.service == LegacyRegime.SA.enrolmentKey && e.state == "Activated")
      if alreadyEnrolled then
        Future.failed(UpstreamErrorResponse("Already enrolled for SA", 409, 409))
      else
        subscriptionWorkItemRepository.findByArnAndRegime(arn, LegacyRegime.SA).flatMap {
          case Some(existing) if existing.status != PermanentlyFailed =>
            Future.failed(UpstreamErrorResponse("SA subscription already in progress", 409, 409))
          case Some(existing) =>
            // SA work items are uniquely keyed by (arn, regime). When a previous attempt is PermanentlyFailed we allow
            // the user to re-start, but must remove the existing document before inserting the new attempt.
            subscriptionWorkItemRepository.deletePermanentlyFailedById(existing.id).flatMap {
              case true => startNewSaWorkItem(arn, subscriptionRequest, adminCredId, groupId)
              case false =>
                // If the document wasn't deleted it has likely been updated concurrently; treat as "in progress".
                Future.failed(UpstreamErrorResponse("SA subscription already in progress", 409, 409))
            }
          case None => startNewSaWorkItem(arn, subscriptionRequest, adminCredId, groupId)
        }
    }

  private def startNewSaWorkItem(
    arn: Arn,
    subscriptionRequest: SaSubscriptionRequest,
    adminCredId: CredId,
    groupId: GroupId
  )(using request: RequestHeader): Future[Done] =
    // Local stub-only: ESP stubs require session + bearer; never persist in QA/Prod.
    val (optSessionId, optBearerToken) = maybeCaptureStubHeaders()
    subscriptionWorkItemRepository
      .pushNew(
        SubscriptionWorkItem(
          arn = arn,
          subscriptionRequest = subscriptionRequest,
          regime = LegacyRegime.SA,
          agentReference = None,
          groupId = Some(groupId),
          adminCredId = Some(adminCredId),
          sessionId = optSessionId,
          bearerToken = optBearerToken
        )
      )
      .map(_ => Done)
      .recoverWith { case NonFatal(error) if isDuplicateKeyException(error) =>
        // `findByArnAndRegime` is not enough under concurrency: two requests can race and the loser will hit the unique
        // (arn, regime) index. Return the intended conflict response in that case.
        Future.failed(UpstreamErrorResponse("SA subscription already in progress", 409, 409))
      }

  def handleRoboticsCallback(
    callback: SubscriptionCallback
  ): Future[SubscriptionService.CallbackHandling] =
    callback.status match {
      case CallbackSuccess =>
        // Callback success does not mean subscription is complete; it unblocks the next workflow stage.
        // We set the agentReference and return the work item to ToDo so the post-callback worker (APB-10570)
        // can pick it up.
        subscriptionWorkItemRepository.addAgentReference(callback.agentId, callback.requestId).map {
          case true => SubscriptionService.CallbackHandling.Handled
          case false => SubscriptionService.CallbackHandling.NotFound
        }
      case CallbackFailure =>
        subscriptionWorkItemRepository.handleFailureCallback(callback.requestId).map {
          case SubscriptionWorkItemRepository.FailureCallbackHandling.MarkedPermanentlyFailed =>
            logger.error(s"[handleRoboticsCallback] Robotics callback for requestId ${callback.requestId} returned failed status, reason: '${callback.requestMessage}', marking work item as permanently failed")
            SubscriptionService.CallbackHandling.Handled
          case SubscriptionWorkItemRepository.FailureCallbackHandling.AlreadyPermanentlyFailed =>
            logger.warn(s"[handleRoboticsCallback] Duplicate failure callback for requestId ${callback.requestId}, work item already PermanentlyFailed")
            SubscriptionService.CallbackHandling.Handled
          case SubscriptionWorkItemRepository.FailureCallbackHandling.IgnoredAlreadySucceeded =>
            logger.warn(s"[handleRoboticsCallback] Ignoring failure callback for requestId ${callback.requestId} because success has already been recorded")
            SubscriptionService.CallbackHandling.Handled
          case SubscriptionWorkItemRepository.FailureCallbackHandling.NotFound =>
            SubscriptionService.CallbackHandling.NotFound
        }
    }

  def getSubscriptionInfo(
    arn: Arn,
    groupId: GroupId,
    regimes: Seq[LegacyRegime]
  )(using requestHeader: RequestHeader): Future[Seq[SubscriptionInfo]] = Future.sequence(regimes.map { regime =>
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

object SubscriptionService:
  enum CallbackHandling:
    case Handled
    case NotFound
