/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.mocks

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.models.AgentCheckOutcome
import uk.gov.hmrc.agentservicesaccount.models.EntityCheckNotification
import uk.gov.hmrc.agentservicesaccount.services.AuditService
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.Future

trait MockAuditService
extends MockitoSugar { this: TestSuite =>

  val mockAuditService: AuditService = mock[AuditService]

  def mockAuditEntityChecksPerformed(): Unit = {
    when(
      mockAuditService.auditEntityChecksPerformed(
        any[Arn],
        any[Option[Utr]],
        any[Seq[AgentCheckOutcome]]
      )(using any[RequestHeader])
    ).thenReturn(Future.successful(AuditResult.Success))
  }

  def mockAuditEntityCheckFailureNotificationSent(): Unit = {
    when(
      mockAuditService.auditEntityCheckFailureNotificationSent(
        any[EntityCheckNotification]
      )(using any[RequestHeader])
    ).thenReturn(Future.successful(AuditResult.Success))
  }

}
