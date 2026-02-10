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

import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.connectors.AgentAssuranceConnector
import uk.gov.hmrc.agentservicesaccount.connectors.CitizenDetailsConnector
import uk.gov.hmrc.agentservicesaccount.connectors.DesConnector
import uk.gov.hmrc.agentservicesaccount.models.agententity.EmailCheckExceptions
import uk.gov.hmrc.agentservicesaccount.models.agententity.EntityCheckException
import uk.gov.hmrc.agentservicesaccount.models.agententity.EntityCheckResult
import uk.gov.hmrc.agentservicesaccount.models.agententity.RefusalCheckException
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.models.AgentCheckOutcome
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.models.EntityCheckNotification
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException.EntityDeceasedCheckFailed
import uk.gov.hmrc.agentservicesaccount.models.agententity.RefusalCheckException.AgentIsOnRefuseToDealList
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException.*
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentDetailsService @Inject() (
  desConnector: DesConnector,
  citizenConnector: CitizenDetailsConnector,
  agentAssuranceConnector: AgentAssuranceConnector,
  mongoLockService: MongoLockService,
  emailService: EmailService,
  auditService: AuditService
)(implicit ec: ExecutionContext) {

  def getAgentDetailsWithChecks(
    arn: Arn
  )(using request: RequestHeader): Future[EntityCheckResult] = {

    for {
      agentRecord <- desConnector.getAgentRecord(arn)
      entityChecksResult <- agentRecord.uniqueTaxReference
        .map(getEntityChecks(
          arn,
          _,
          agentRecord.isAnIndividual
        ))
        .getOrElse(Future.successful(Seq.empty[EntityCheckException]))
      _ <- sendEmail(
        agentRecord,
        entityChecksResult,
        arn: Arn
      )
    } yield EntityCheckResult(
      agentRecord,
      entityChecksResult
    )
  }

  private def getEntityChecks(
    arn: Arn,
    utr: Utr,
    isAnIndividual: Option[Boolean]
  )(using request: RequestHeader): Future[Seq[EntityCheckException]] = {
    mongoLockService
      .dailyLock(utr = utr) {
        getRequiredChecks(utr, isAnIndividual)
      }
      .map {
        case Some(entityCheckExceptions) =>
          sendAudit(
            arn,
            utr,
            entityCheckExceptions
          )
          entityCheckExceptions
        case None => Seq.empty[EntityCheckException]
      }
  }

  private def getRequiredChecks(
    utr: Utr,
    isAnIndividual: Option[Boolean]
  )(using request: RequestHeader): Future[Seq[EntityCheckException]] = Future.sequence {
    if (isAnIndividual.contains(true))
      Seq(deceasedStatusCheck(SaUtr(utr.value)), refusalToDealCheck(utr))
    else
      Seq(refusalToDealCheck(utr))
  }.map(_.flatten)

  private def deceasedStatusCheck(saUtr: SaUtr)(using request: RequestHeader): Future[Option[EntityCheckException]] = citizenConnector
    .getCitizenDeceasedFlag(saUtr)

  private def refusalToDealCheck(utr: Utr)(using request: RequestHeader): Future[Option[RefusalCheckException]] = agentAssuranceConnector
    .getAgentUtrChecks(utr)
    .map(_.isRefusalToDealWith)
    .map {
      case true => Some(AgentIsOnRefuseToDealList)
      case false => None
    }

  private def sendAudit(
    arn: Arn,
    utr: Utr,
    entityCheckExceptions: Seq[EntityCheckException]
  )(using request: RequestHeader): Future[AuditResult] = {
    val onRefusalListAgentCheckOutcomes: AgentCheckOutcome = entityCheckExceptions
      .collectFirst {
        case AgentIsOnRefuseToDealList =>
          AgentCheckOutcome(
            agentCheckType = "onRefusalList",
            isSuccessful = false,
            failureReason = Some(AgentIsOnRefuseToDealList.failedChecksText)
          )
      }
      .getOrElse(AgentCheckOutcome(
        agentCheckType = "onRefusalList",
        isSuccessful = true,
        failureReason = None
      ))

    val isDeceasedAgentCheckOutcome: AgentCheckOutcome = entityCheckExceptions
      .collectFirst {
        case EntityDeceasedCheckFailed =>
          AgentCheckOutcome(
            agentCheckType = "isDeceased",
            isSuccessful = false,
            failureReason = Some(EntityDeceasedCheckFailed.failedChecksText)
          )
        case x @ CitizenConnectorRequestFailed(_) =>
          AgentCheckOutcome(
            agentCheckType = "isDeceased",
            isSuccessful = false,
            failureReason = Some(s"Check failed with error: ${x.code.toString}")
          )
      }
      .getOrElse(AgentCheckOutcome(
        agentCheckType = "isDeceased",
        isSuccessful = true,
        failureReason = None
      ))

    auditService.auditEntityChecksPerformed(
      arn,
      Some(utr),
      agentCheckOutcomes = Seq(isDeceasedAgentCheckOutcome, onRefusalListAgentCheckOutcomes)
    )

  }

  private def sendEmail(
    agentRecord: AgentDetailsDesResponse,
    entityCheckExceptions: Seq[EntityCheckException],
    arn: Arn
  )(using request: RequestHeader): Future[Unit] = {
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy h:mma")

    val failedChecks: Seq[String] = entityCheckExceptions.collect {
      case x: EmailCheckExceptions => x.failedChecksText
    }

    (agentRecord.uniqueTaxReference, failedChecks) match {
      case (Some(utr), nonEmptyFailedChecks) if nonEmptyFailedChecks.nonEmpty =>
        val entityCheckNotification = EntityCheckNotification(
          arn = arn,
          utr = utr.value,
          agencyName = agentRecord.agencyDetails.flatMap(_.agencyName).getOrElse(""),
          failedChecks = nonEmptyFailedChecks.mkString("|"),
          dateTime = formatter.format(LocalDateTime.now())
        )

        mongoLockService
          .emailLock(utr) {
            emailService.sendEntityCheckNotification(entityCheckNotification)
          }
          .map {
            case Some(_) =>
              auditService
                .auditEntityCheckFailureNotificationSent(entityCheckNotification)
                .map(_ => ())
            case None => ()
          }

      case _ => Future.successful(())
    }
  }

}
