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
import uk.gov.hmrc.agentservicesaccount.connectors.EmailConnector
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionWorkItem
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class LegacySubscriptionEmailService @Inject() (
  emailConnector: EmailConnector
)(using ec: ExecutionContext)
extends Logging {

  def sendFailureEmailIgnoreErrors(
    workItem: SubscriptionWorkItem
  ): Future[Unit] = sendFailureEmail(workItem)
    .recover { case NonFatal(error) =>
      logger.warn(
        s"[LegacySubscriptionEmailService] Failed to send failure email for request ${workItem.requestId}",
        error
      )
    }

  def sendCompletionEmailIgnoreErrors(
    workItem: SubscriptionWorkItem
  ): Future[Unit] = sendCompletionEmail(workItem)
    .recover { case NonFatal(error) =>
      logger.warn(s"[LegacySubscriptionEmailService] Failed to send completion email for request ${workItem.requestId}", error)
    }

  private def sendFailureEmail(
    workItem: SubscriptionWorkItem
  ): Future[Unit] =
    workItem.subscriptionRequest.emailAddress match {
      case Some(email) =>
        given RequestHeader = RequestSupport.thereIsNoRequest

        emailConnector.sendEmail(
          EmailInformation(
            to = Seq(email),
            templateId = templateId(
              "agent_services_subscription_fail",
              workItem.subscriptionRequest.isWelsh
            ),
            parameters = Map(
              "agencyName" -> workItem.subscriptionRequest.agentName,
              "serviceName" -> serviceName(workItem)
            )
          )
        )

      case None => Future.unit
    }

  private def sendCompletionEmail(workItem: SubscriptionWorkItem): Future[Unit] =
    (workItem.subscriptionRequest.emailAddress, workItem.agentReference) match
      case (Some(email), Some(agentReference)) =>
        given play.api.mvc.RequestHeader = RequestSupport.thereIsNoRequest

        emailConnector.sendEmail(
          EmailInformation(
            to = Seq(email),
            templateId = templateId(
              "agent_services_subscription_complete",
              workItem.subscriptionRequest.isWelsh
            ),
            parameters = Map(
              "agencyName" -> workItem.subscriptionRequest.agentName,
              "arn" -> workItem.arn.value,
              "serviceName" -> serviceName(workItem),
              "serviceSectionName" -> serviceSectionName(workItem),
              "agentCode" -> agentReference.value
            )
          )
        )
      case _ => Future.unit

  private def templateId(
    base: String,
    isWelsh: Boolean
  ): String =
    s"$base${if (isWelsh)
        "_cy"
      else
        ""}"

  private def serviceName(workItem: SubscriptionWorkItem): String =
    val isWelsh = workItem.subscriptionRequest.isWelsh
    workItem.regime match
      case LegacyRegime.PAYE =>
        if (isWelsh)
          "TWE/CIS"
        else
          "PAYE/CIS"
      case LegacyRegime.SA =>
        if (isWelsh)
          "Hunanasesiad"
        else
          "Self Assessment"
      case LegacyRegime.CT =>
        if (isWelsh)
          "Treth Gorfforaeth"
        else
          "Corporation Tax"

  private def serviceSectionName(workItem: SubscriptionWorkItem): String =
    val isWelsh = workItem.subscriptionRequest.isWelsh
    workItem.regime match
      case LegacyRegime.PAYE =>
        if (isWelsh)
          "Talu wrth ennill (TWE)/Cynllun y Diwydiant Adeiladu (CIS)"
        else
          "Pay as you earn (PAYE)/Construction Industry Scheme (CIS)"
      case LegacyRegime.SA =>
        if (isWelsh)
          "Hunanasesiad"
        else
          "Self Assessment"
      case LegacyRegime.CT =>
        if (isWelsh)
          "Treth Gorfforaeth"
        else
          "Corporation Tax"

}
