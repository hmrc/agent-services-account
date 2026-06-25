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
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.Enrolment
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentReference
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayePostcode
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayeSubscriptionRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.http.UpstreamErrorResponse
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

  private val alreadySubscribedFailureReason = "Agent already subscribed"

  def runOnce(using
    jobConfig: WorkItemJobConfig,
    regime: LegacyRegime
  ): Future[Done] = workItemService.pullOutstanding(regime, jobConfig.retryInterval).flatMap {
    case None => Future.successful(Done)
    case Some(workItem) =>
      process(workItem).recoverWith {
        case error: UpstreamErrorResponse if error.message.startsWith(alreadySubscribedFailureReason) =>
          logger.warn(s"[KnownFactsWorker] $regime agent already subscribed, permanently failing work item ${workItem.id}")
          handleAlreadySubscribed(workItem, regime)
        case NonFatal(error) =>
          logger.warn(s"[KnownFactsWorker] $regime known facts failed for work item ${workItem.id}", error)
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
            case Some(_) =>
              handleSuccess(
                workItem,
                agentReference
              )
          }

  private def allocateAgentEnrolment(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: String
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[Unit] = allocateAgentEnrolment(
    workItem = workItem,
    agentReference = agentReference,
    adminCredId = workItem.item.adminCredId
  ).recoverWith {
    case error: UpstreamErrorResponse if hasInvalidCredentialId(error) =>
      logger.warn(s"[KnownFactsWorker] $regime ES8 rejected admin cred id for work item ${workItem.id}; looking up another admin")
      usersGroupsSearchConnector.getFirstAdminCredId(workItem.item.groupId).flatMap {
        case Some(adminCredId) =>
          logger.info(s"[KnownFactsWorker] $regime retrying ES8 allocation with replacement admin for work item: ${workItem.id}")
          allocateAgentEnrolment(
            workItem = workItem,
            agentReference = agentReference,
            adminCredId = adminCredId
          )
        case None =>
          logger.warn(s"[KnownFactsWorker] $regime no replacement admin cred id found for work item: ${workItem.id}")
          Future.failed(error)
      }
    case error: UpstreamErrorResponse
        if hasMultipleEnrolmentsConflict(error) =>
      logger.warn(s"[KnownFactsWorker] $regime received MULTIPLE_ENROLMENTS_INVALID for work item ${workItem.id}")
      handleMultipleEnrolmentsConflict(workItem, agentReference)
  }

  private def allocateAgentEnrolment(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: String,
    adminCredId: CredId
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[Unit] = enrolmentStoreProxyConnector.allocateAgentEnrolment(
    regime = regime,
    groupId = workItem.item.groupId,
    agentReference = agentReference,
    adminCredId = adminCredId
  )

  private def hasInvalidCredentialId(error: UpstreamErrorResponse): Boolean =
    val invalidCredentialId = "INVALID_CREDENTIAL_ID"

    def hasInvalidCredentialId(errorJson: JsValue): Boolean =
      (errorJson \ "code").asOpt[String].contains(invalidCredentialId) ||
        (errorJson \ "errors").asOpt[Seq[JsValue]].exists(_.exists(error => (error \ "code").asOpt[String].contains(invalidCredentialId)))

    error.message.contains(invalidCredentialId) ||
    (try hasInvalidCredentialId(Json.parse(error.message))
    catch case NonFatal(_) => false)

  private def hasMultipleEnrolmentsConflict(error: UpstreamErrorResponse): Boolean =
    val multipleEnrolmentsInvalid = "MULTIPLE_ENROLMENTS_INVALID"

    def extractCode(json: JsValue): Boolean =
      (json \ "code").asOpt[String].contains(multipleEnrolmentsInvalid) ||
        (json \ "errors").asOpt[Seq[JsValue]]
          .exists(_.exists(e => (e \ "code").asOpt[String].contains(multipleEnrolmentsInvalid)))

    error.statusCode == 409 &&
      (try extractCode(Json.parse(error.message))
      catch case NonFatal(_) => false)

  private def isActive(enrolment: Enrolment): Boolean = enrolment.state.equalsIgnoreCase("Activated")

  private def postcodeFor(workItem: SubscriptionWorkItem): Option[PayePostcode.Valid] =
    workItem.subscriptionRequest match {
      case request: PayeSubscriptionRequest => PayePostcode.from(request.address.postCode)
      case _ => None
    }

  private def handleSuccess(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: AgentReference
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[Done] =
    for {
      _ <- allocateAgentEnrolment(workItem, agentReference.value)
      _ <- legacySubscriptionAuditService.auditSuccess(
        arn = workItem.item.arn,
        regime = regime,
        legacyAgentCode = Some(agentReference.value)
      )
      _ <- legacySubscriptionEmailService.sendCompletionEmailIgnoreErrors(workItem.item)
      done <- workItemService.complete(workItem)
    } yield done

  private def handleFailure(workItem: WorkItem[SubscriptionWorkItem])(using jobConfig: WorkItemJobConfig): Future[Done] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then {
      for {
        _ <- legacySubscriptionAuditService.auditFailure(
          arn = workItem.item.arn,
          regime = workItem.item.regime,
          failureReason = "Max retry attempts reached in KnownFactsWorker"
        )
        _ <- legacySubscriptionEmailService.sendFailureEmailIgnoreErrors(workItem.item)
        result <- workItemService.markPermanentlyFailed(workItem)
      } yield result
    }
    else
      workItemService.markFailed(workItem)

  private def handleMultipleEnrolmentsConflict(
    workItem: WorkItem[SubscriptionWorkItem],
    agentReference: String
  )(using
    hc: HeaderCarrier,
    regime: LegacyRegime
  ): Future[Unit] = enrolmentStoreProxyConnector
    .queryEnrolmentsAllocatedToGroupHC(workItem.item.groupId)
    .flatMap { enrolments =>
      enrolments.find(_.service == regime.enrolmentKey) match {
        case Some(enrolment) if isActive(enrolment) =>
          logger.warn(s"[KnownFactsWorker] $regime active enrolment already exists for group ${workItem.item.groupId.value}")
          Future.failed(
            UpstreamErrorResponse(
              message = s"$alreadySubscribedFailureReason: $regime",
              statusCode = 409,
              reportAs = 409
            )
          )
        case Some(_) =>
          logger.warn(s"[KnownFactsWorker] $regime inactive enrolment found; deallocating and retrying ES8")
          for {
            _ <- enrolmentStoreProxyConnector.deallocateAgentEnrolment(
              groupId = workItem.item.groupId,
              regime = regime,
              agentReference = agentReference
            )
            _ <- allocateAgentEnrolment(
              workItem = workItem,
              agentReference = agentReference,
              adminCredId = workItem.item.adminCredId
            )
          } yield ()
        case None =>
          logger.warn(s"[KnownFactsWorker] $regime received MULTIPLE_ENROLMENTS_INVALID but ES3 found no matching enrolment")
          Future.failed(
            new RuntimeException(
              s"MULTIPLE_ENROLMENTS_INVALID received but no ${regime.enrolmentKey} enrolment exists"
            )
          )
      }
    }

  private def handleAlreadySubscribed(
    workItem: WorkItem[SubscriptionWorkItem],
    regime: LegacyRegime
  ): Future[Done] =
    for {
      _ <- legacySubscriptionAuditService.auditFailure(
        arn = workItem.item.arn,
        regime = regime,
        failureReason = s"Agent already subscribed to $regime"
      )
      result <- workItemService.markPermanentlyFailed(workItem)
    } yield result
