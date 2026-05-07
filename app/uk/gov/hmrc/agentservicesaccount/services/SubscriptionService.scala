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
import org.mongodb.scala.MongoException
import play.api.Logging
import play.api.http.Status.CONFLICT
import play.api.http.Status.TOO_MANY_REQUESTS
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

@Singleton
class SubscriptionService @Inject() (
  agentEpayeRegistrationConnector: AgentEpayeRegistrationConnector,
  subscriptionWorkItemRepository: SubscriptionWorkItemRepository,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  agentMappingConnector: AgentMappingConnector,
  appConfig: AppConfig,
  agentEntityTypeService: AgentEntityTypeService
)(using ec: ExecutionContext)
extends Logging:

  private def maybeCaptureStubHeaders()(using request: RequestHeader): (Option[String], Option[String]) =
    if (appConfig.stubsCompatibilityMode)
      (RequestSupport.hc.sessionId.map(_.value), RequestSupport.hc.authorization.map(_.value))
    else
      (None, None)

  private def checkExistingEnrolments(
    regime: LegacyRegime,
    groupId: GroupId
  )(using request: RequestHeader): Future[Done] = enrolmentStoreProxyConnector.queryEnrolmentsAllocatedToGroup(groupId).map { enrolments =>
    if enrolments.exists(e => e.service == regime.enrolmentKey && e.state == "Activated") then
      throw UpstreamErrorResponse(
        message = s"Already enrolled for ${regime.toString}",
        statusCode = CONFLICT,
        reportAs = CONFLICT
      )
    else Done
  }

  private def checkExistingWorkItem(
    arn: Arn,
    regime: LegacyRegime
  ): Future[Done] = subscriptionWorkItemRepository.findByArnAndRegime(arn, regime).flatMap {
    case Some(existing) if existing.status != PermanentlyFailed =>
      Future.failed(UpstreamErrorResponse(
        message = s"${regime.toString} subscription already in progress",
        statusCode = CONFLICT,
        reportAs = CONFLICT
      ))
    case Some(existing) =>
      // Work items are uniquely keyed by (arn, regime). When a previous attempt is PermanentlyFailed we allow
      // the user to re-start, but must remove the existing document before inserting the new attempt.
      subscriptionWorkItemRepository.deletePermanentlyFailedById(existing.id).map {
        case true => Done
        case false =>
          // If the document wasn't deleted it has likely been updated concurrently; treat as "in progress".
          throw UpstreamErrorResponse(
            message = s"${regime.toString} subscription already in progress",
            statusCode = CONFLICT,
            reportAs = CONFLICT
          )
      }
    case None => Future.successful(Done)
  }

  def startSubscriptionProcess(
    arn: Arn,
    subscriptionRequest: SubscriptionRequest,
    regime: LegacyRegime,
    adminCredId: CredId,
    groupId: GroupId
  )(using request: RequestHeader): Future[Done] =
    for {
      _ <- checkExistingEnrolments(
        regime,
        groupId
      )
      _ <- checkExistingWorkItem(arn, regime)
      workItem <-
        subscriptionRequest match {
          case request: PayeSubscriptionRequest =>
            createPayeWorkItem(
              arn,
              request,
              adminCredId,
              groupId
            )
          case request: (SaSubscriptionRequest | CtSubscriptionRequest) =>
            createWorkItem(
              arn,
              request,
              regime,
              adminCredId,
              groupId
            )
        }
      result <- subscriptionWorkItemRepository
        .pushNew(workItem)
        .map(_ => Done)
        .recoverWith {
          case e: MongoException if e.getMessage.contains("E11000") =>
            // `findByArnAndRegime` is not enough under concurrency: two requests can race and the loser will hit the unique
            // (arn, regime) index. Return the intended conflict response in that case.
            Future.failed(UpstreamErrorResponse(
              s"${regime.toString} subscription already in progress",
              TOO_MANY_REQUESTS,
              TOO_MANY_REQUESTS
            ))
        }
    } yield result

  private def createPayeWorkItem(
    arn: Arn,
    subscriptionRequest: PayeSubscriptionRequest,
    adminCredId: CredId,
    groupId: GroupId
  )(using request: RequestHeader): Future[SubscriptionWorkItem] =
    // PAYE allows us to create an agent reference and trigger known fact setup up front, so we can create the work item that skips the callback process
    agentEpayeRegistrationConnector.register(subscriptionRequest).map { agentReference =>
      // Local stub-only: ESP stubs require session + bearer; never persist in QA/Prod.
      val (optSessionId, optBearerToken) = maybeCaptureStubHeaders()

      SubscriptionWorkItem(
        arn = arn,
        subscriptionRequest = subscriptionRequest,
        regime = PAYE,
        agentReference = Some(agentReference),
        groupId = groupId,
        adminCredId = adminCredId,
        sessionId = optSessionId,
        bearerToken = optBearerToken
      )
    }

  private def createWorkItem(
    arn: Arn,
    subscriptionRequest: SaSubscriptionRequest | CtSubscriptionRequest,
    regime: LegacyRegime,
    adminCredId: CredId,
    groupId: GroupId
  )(using request: RequestHeader): Future[SubscriptionWorkItem] =
    // Local stub-only: ESP stubs require session + bearer; never persist in QA/Prod.
    val (optSessionId, optBearerToken) = maybeCaptureStubHeaders()

    agentEntityTypeService.resolve(arn).map { entityType =>
      SubscriptionWorkItem(
        arn = arn,
        subscriptionRequest = subscriptionRequest,
        regime = regime,
        agentReference = None,
        groupId = groupId,
        adminCredId = adminCredId,
        sessionId = optSessionId,
        bearerToken = optBearerToken,
        entityType = entityType
      )
    }

  def handleRoboticsCallback(
    callback: SubscriptionCallback
  ): Future[SubscriptionService.CallbackHandling] =
    callback.status match {
      case CallbackSuccess =>
        // Callback success does not mean subscription is complete; it unblocks the next workflow stage.
        // We set the agentReference and return the work item to ToDo so the post-callback worker (APB-10570)
        // can pick it up.
        subscriptionWorkItemRepository.addAgentReference(
          callback.agentId.getOrElse(throw new RuntimeException("missing agentId after validating model")),
          callback.requestId
        ).map {
          case true => SubscriptionService.CallbackHandling.Handled
          case false => SubscriptionService.CallbackHandling.NotFound
        }
      case CallbackFailure =>
        subscriptionWorkItemRepository.handleFailureCallback(callback.requestId).map {
          case SubscriptionWorkItemRepository.FailureCallbackHandling.NotFound => SubscriptionService.CallbackHandling.NotFound
          case _ => SubscriptionService.CallbackHandling.Handled
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
            subscriptionStatus = SubscriptionStatus.fromProcessingStatus(workItem.status),
            creationDate = Some(workItem.receivedAt)
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
