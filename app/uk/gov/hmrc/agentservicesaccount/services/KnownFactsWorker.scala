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

import org.apache.pekko.Done
import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.agentservicesaccount.models.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport
import uk.gov.hmrc.http.*
import uk.gov.hmrc.mongo.workitem.WorkItem

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class KnownFactsWorker @Inject() (
  workItemService: KnownFactsWorkItemService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  usersGroupsSearchConnector: UsersGroupsSearchConnector,
  legacySubscriptionAuditService: LegacySubscriptionAuditService,
  legacySubscriptionEmailService: LegacySubscriptionEmailService
)(using ec: ExecutionContext)
extends Logging:

  def runOnce(using
    jobConfig: WorkItemJobConfig,
    regime: LegacyRegime
  ): Future[Done] = workItemService.pullOutstanding(regime, jobConfig.retryInterval).flatMap {
    case None => Future.successful(Done)
    case Some(workItem) =>
      process(workItem).recoverWith {
        case NonFatal(error) =>
          logger.warn(s"[KnownFactsWorker] $regime failed for work item ${workItem.id}", error)
          handleFailure(workItem)
      }
  }

  private def process(workItem: WorkItem[SubscriptionWorkItem])(using
    jobConfig: WorkItemJobConfig,
    regime: LegacyRegime
  ): Future[Done] =
    (regime, workItem.item.agentReference, postcodeFor(workItem.item)) match
      case (_, None, _) =>
        logger.error(s"[KnownFactsWorker] $regime work item missing agent reference: ${workItem.id}" +
          s"(this should not be possible as the mongo query requires an agent reference to be present)")
        handleFailure(workItem)
      case (LegacyRegime.PAYE, Some(_), None) =>
        logger.warn(s"[KnownFactsWorker] PAYE work item missing usable postcode for ES20 lookup: ${workItem.id}")
        workItemService.markPermanentlyFailed(workItem)
      case (_, Some(agentReference), postcode) =>
        // Local stubs expect auth/session headers; QA/Prod use internal auth and leave these empty.
        given HeaderCarrier = HeaderCarrier(
          authorization = workItem.item.bearerToken.map(Authorization.apply),
          sessionId = workItem.item.sessionId.map(SessionId.apply)
        )
        enrolmentStoreProxyConnector.queryKnownFactsForAgent(
          regime,
          agentReference.value,
          postcode
        )
          .flatMap {
            case None =>
              logger.info(s"[KnownFactsWorker] $regime known facts not available yet for work item: ${workItem.id}")
              handleFailure(workItem)
            case Some(_) => handleSuccess(workItem, agentReference)
          }

  private def handleSuccess(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: AgentReference
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[Done] = allocateAgentEnrolment(workItem, agentReference.value).flatMap {
    case AllocationOutcome.Success |
        AllocationOutcome.RetriedAfterConflict =>
      for {
        _ <- legacySubscriptionAuditService.auditSuccess(
          arn = workItem.item.arn,
          regime = regime,
          legacyAgentCode = Some(agentReference.value)
        )
        _ <- legacySubscriptionEmailService.sendCompletionEmailIgnoreErrors(workItem.item)
        done <- workItemService.complete(workItem)
      } yield done
    case AllocationOutcome.MissingEnrolment => Future.failed(new RuntimeException("Could not find enrolment while dealing with MultipleEnrolmentsConflict"))
    case AllocationOutcome.MissingAgentReference =>
      Future.failed(new RuntimeException("Could not find agent reference in inactive enrolment while dealing with MultipleEnrolmentsConflict"))
    case AllocationOutcome.AlreadySubscribed => Future.successful(Done)
  }

  private def allocateAgentEnrolment(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: String
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[AllocationOutcome] = enrolmentStoreProxyConnector
    .allocateAgentEnrolment(
      regime = regime,
      groupId = workItem.item.groupId,
      agentReference = agentReference,
      adminCredId = workItem.item.adminCredId
    )
    .map(_ => AllocationOutcome.Success)
    .recoverWith {
      case error: UpstreamErrorResponse if hasInvalidCredentialId(error) =>
        usersGroupsSearchConnector.getFirstAdminCredId(workItem.item.groupId).flatMap {
          case Some(adminCredId) =>
            enrolmentStoreProxyConnector.allocateAgentEnrolment(
              regime,
              workItem.item.groupId,
              agentReference,
              adminCredId
            )
              .map(_ => AllocationOutcome.RetriedAfterConflict)

          case None => Future.failed(error)
        }

      case error: UpstreamErrorResponse if hasMultipleEnrolmentsConflict(error) => handleMultipleEnrolmentsConflict(workItem, agentReference)
    }

  private def handleMultipleEnrolmentsConflict(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: String
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[AllocationOutcome] =
    given RequestHeader = RequestSupport.thereIsNoRequest
    enrolmentStoreProxyConnector
      .queryEnrolmentsAllocatedToGroup(workItem.item.groupId)
      .flatMap { enrolments =>
        enrolments.find(_.service == regime.enrolmentKey) match {
          case Some(enrolment) if isActive(enrolment) => failAsAlreadySubscribed(workItem, regime)
          case Some(inactiveEnrolment) =>
            inactiveEnrolment.identifiers.find(_.key == regime.agentReferenceKey).map(_.value) match {
              case Some(existingAgentReference) =>
                for {
                  _ <- enrolmentStoreProxyConnector.deallocateAgentEnrolment(
                    workItem.item.groupId,
                    regime,
                    existingAgentReference
                  )
                  _ <- enrolmentStoreProxyConnector.allocateAgentEnrolment(
                    regime,
                    workItem.item.groupId,
                    agentReference,
                    workItem.item.adminCredId
                  )
                } yield AllocationOutcome.RetriedAfterConflict
              case None => Future.successful(AllocationOutcome.MissingAgentReference)
            }
          case None => Future.successful(AllocationOutcome.MissingEnrolment)
        }
      }

  private def failAsAlreadySubscribed(
    workItem: WorkItem[SubscriptionWorkItem],
    regime: LegacyRegime
  ): Future[AllocationOutcome] =
    logger.warn(
      s"[KnownFactsWorker] $regime active enrolment already exists for group ${workItem.item.groupId.value}"
    )
    for {
      _ <- legacySubscriptionAuditService.auditFailure(
        arn = workItem.item.arn,
        regime = regime,
        failureReason = s"Agent already subscribed to $regime"
      )
      _ <- workItemService.markPermanentlyFailed(workItem)
    } yield AllocationOutcome.AlreadySubscribed

  private def handleFailure(workItem: WorkItem[SubscriptionWorkItem])(using
    jobConfig: WorkItemJobConfig
  ): Future[Done] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then
      for {
        _ <- legacySubscriptionAuditService.auditFailure(
          arn = workItem.item.arn,
          regime = workItem.item.regime,
          failureReason = "Max retry attempts reached in KnownFactsWorker"
        )
        _ <- legacySubscriptionEmailService.sendFailureEmailIgnoreErrors(workItem.item)
        done <- workItemService.markPermanentlyFailed(workItem)
      } yield done
    else
      workItemService.markFailed(workItem)

  private def isActive(enrolment: Enrolment): Boolean = enrolment.state.equalsIgnoreCase("Activated")

  private def postcodeFor(workItem: SubscriptionWorkItem): Option[PayePostcode.Valid] =
    workItem.subscriptionRequest match {
      case r: PayeSubscriptionRequest => PayePostcode.from(r.address.postCode)
      case _ => None
    }

  private def hasMultipleEnrolmentsConflict(error: UpstreamErrorResponse): Boolean =
    error.statusCode == 409 &&
      error.message.contains("MULTIPLE_ENROLMENTS_INVALID")

  private def hasInvalidCredentialId(error: UpstreamErrorResponse): Boolean = error.message.contains("INVALID_CREDENTIAL_ID")

sealed trait AllocationOutcome
object AllocationOutcome:

  case object Success
  extends AllocationOutcome
  case object RetriedAfterConflict
  extends AllocationOutcome
  case object AlreadySubscribed
  extends AllocationOutcome
  case object MissingEnrolment
  extends AllocationOutcome
  case object MissingAgentReference
  extends AllocationOutcome
