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

import play.api.libs.json.Json
import play.api.libs.json.Writes
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.audit.AgentCheckAuditEvent
import uk.gov.hmrc.agentservicesaccount.models.audit.AgentCheckFailureNotificationAuditEvent
import uk.gov.hmrc.agentservicesaccount.models.audit.AuditDetail
import uk.gov.hmrc.agentservicesaccount.models.audit.EmailData
import uk.gov.hmrc.agentservicesaccount.models.audit.LegacySubscriptionAuditEvent
import uk.gov.hmrc.agentservicesaccount.models.AgentCheckOutcome
import uk.gov.hmrc.agentservicesaccount.models.EntityCheckNotification
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport
import uk.gov.hmrc.agentservicesaccount.utils.RequestSupport.given
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AuditService @Inject() (
  appConfig: AppConfig,
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext) {

  def auditEntityChecksPerformed(
    arn: Arn,
    utr: Option[Utr],
    agentCheckOutcomes: Seq[AgentCheckOutcome]
  )(using request: RequestHeader): Future[AuditResult] = audit(AgentCheckAuditEvent(
    arn,
    utr,
    agentCheckOutcomes
  ))

  def auditEntityCheckFailureNotificationSent(
    entityCheckNotification: EntityCheckNotification
  )(using request: RequestHeader): Future[AuditResult] = audit(
    AgentCheckFailureNotificationAuditEvent(
      agentReferenceNumber = entityCheckNotification.arn,
      utr = entityCheckNotification.utr,
      email = appConfig.agentMaintainerEmail,
      emailData = EmailData(
        Seq(entityCheckNotification.failedChecks),
        dateChecked = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
      )
    )
  )

  def auditLegacySubscription(
    arn: Arn,
    regime: LegacyRegime,
    isSuccessful: Boolean,
    legacyAgentCode: Option[String],
    failureReason: Option[String]
  )(using request: RequestHeader): Future[AuditResult] = {

    audit(
      LegacySubscriptionAuditEvent(
        agentReferenceNumber = arn,
        legacyAgentService = regime.enrolmentKey,
        isSuccessful = isSuccessful,
        legacyAgentCode = legacyAgentCode.filter(_ => isSuccessful || legacyAgentCode.isDefined),
        failureReason =
          if (!isSuccessful)
            failureReason
          else
            None
      )
    )
  }

  private def audit[A <: AuditDetail: Writes](a: A)(using request: RequestHeader): Future[AuditResult] = {
    auditConnector
      .sendExtendedEvent(
        ExtendedDataEvent(
          auditSource = auditSource,
          auditType = a.auditType,
          eventId = UUID.randomUUID().toString,
          detail = Json.toJson(a),
          tags = hc.toAuditTags()
        )
      )
  }

  private val auditSource = "agent-services-account"

}
