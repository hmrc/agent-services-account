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
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class LegacySubscriptionAuditService @Inject() (
  auditService: AuditService
)(using ExecutionContext)
extends Logging {

  given RequestHeader = RequestSupport.thereIsNoRequest

  def auditSuccess(
    arn: Arn,
    regime: LegacyRegime,
    legacyAgentCode: Option[String]
  ): Future[Unit] = auditService.auditLegacySubscription(
    arn = arn,
    regime = regime,
    isSuccessful = true,
    legacyAgentCode = legacyAgentCode,
    failureReason = None
  )
    .map(_ => ())
    .recover { case NonFatal(error) =>
      logger.warn(
        s"[LegacySubscriptionAuditService] Failed to audit successful legacy subscription for arn ${arn.value}",
        error
      )
    }

  def auditFailure(
    arn: Arn,
    regime: LegacyRegime,
    failureReason: String
  ): Future[Unit] = auditService.auditLegacySubscription(
    arn = arn,
    regime = regime,
    isSuccessful = false,
    legacyAgentCode = None,
    failureReason = Some(failureReason)
  )
    .map(_ => ())
    .recover { case NonFatal(error) =>
      logger.warn(
        s"[LegacySubscriptionAuditService] Failed to audit failed legacy subscription for arn ${arn.value}",
        error
      )
    }

}
