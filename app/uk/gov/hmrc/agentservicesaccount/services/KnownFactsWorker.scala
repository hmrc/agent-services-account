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
import uk.gov.hmrc.agentservicesaccount.connectors.EmailConnector
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayeSubscriptionRequest
import uk.gov.hmrc.agentservicesaccount.models.subscription.PayePostcode
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport
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
  emailConnector: EmailConnector,
  legacySubscriptionAuditService: LegacySubscriptionAuditService
)(using ec: ExecutionContext)
extends Logging:

  def runOnce(using
    jobConfig: WorkItemJobConfig,
    regime: LegacyRegime
  ): Future[Done] = workItemService.pullOutstanding(regime, jobConfig.retryInterval).flatMap {
    case None => Future.successful(Done)
    case Some(workItem) =>
      process(workItem).recoverWith { case NonFatal(error) =>
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
        ).flatMap {
          case None =>
            logger.info(s"[KnownFactsWorker] $regime known facts not available yet for work item: ${workItem.id}")
            handleFailure(workItem)
          case Some(_) =>
            allocateAgentEnrolment(workItem, agentReference.value)
              .flatMap { _ =>
                logger.info(s"[KnownFactsWorker] $regime enrolment allocated for work item: ${workItem.id}")
                legacySubscriptionAuditService
                  .auditSuccess(
                    arn = workItem.item.arn,
                    regime = regime,
                    legacyAgentCode = Some(agentReference.value)
                  )
                  .flatMap { _ =>
                    sendCompletionEmail(workItem.item)
                      .recover { case NonFatal(error) =>
                        logger.warn(s"[KnownFactsWorker] $regime completion email failed for work item ${workItem.id}", error)
                      }
                      .flatMap(_ => workItemService.complete(workItem))
                  }
              }
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

  private def postcodeFor(workItem: SubscriptionWorkItem): Option[PayePostcode.Valid] =
    workItem.subscriptionRequest match {
      case request: PayeSubscriptionRequest => PayePostcode.from(request.address.postCode)
      case _ => None
    }

  private def sendCompletionEmail(workItem: SubscriptionWorkItem)(using regime: LegacyRegime): Future[Unit] =
    (workItem.subscriptionRequest.emailAddress, workItem.agentReference) match
      case (Some(email), Some(agentReference)) =>
        given play.api.mvc.RequestHeader = RequestSupport.thereIsNoRequest
        emailConnector.sendEmail(
          EmailInformation(
            to = Seq(email),
            templateId = "agent_services_subscription_complete",
            parameters = Map(
              "agencyName" -> workItem.subscriptionRequest.agentName,
              "arn" -> workItem.arn.value,
              "serviceName" -> serviceName(regime),
              "serviceSectionName" -> serviceSectionName(regime),
              "agentCode" -> agentReference.value
            )
          )
        )
      case _ => Future.unit

  private def serviceName(regime: LegacyRegime): String =
    regime match
      case LegacyRegime.PAYE => "PAYE/CIS"
      case LegacyRegime.SA => "Self Assessment"
      case LegacyRegime.CT => "Corporation Tax"

  private def serviceSectionName(regime: LegacyRegime): String =
    regime match
      case LegacyRegime.PAYE => "Pay as you earn (PAYE)/Construction Industry Scheme (CIS)"
      case LegacyRegime.SA => "Self Assessment"
      case LegacyRegime.CT => "Corporation Tax"

  private def handleFailure(workItem: WorkItem[SubscriptionWorkItem])(using jobConfig: WorkItemJobConfig): Future[Done] =
    if workItem.failureCount + 1 >= jobConfig.maxAttempts then
      legacySubscriptionAuditService
        .auditFailure(
          arn = workItem.item.arn,
          regime = workItem.item.regime,
          failureReason = "Max retry attempts reached in KnownFactsWorker"
        )
        .flatMap(_ => workItemService.markPermanentlyFailed(workItem))
    else
      workItemService.markFailed(workItem)
