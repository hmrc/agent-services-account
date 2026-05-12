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

package uk.gov.hmrc.agentservicesaccount.mocks

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime
import uk.gov.hmrc.agentservicesaccount.services.LegacySubscriptionAuditService

import scala.concurrent.Future

trait MockLegacySubscriptionAuditService
extends MockitoSugar { this: TestSuite =>

  val mockLegacySubscriptionAuditService: LegacySubscriptionAuditService = mock[LegacySubscriptionAuditService]

  def mockLegacySubscriptionAuditSuccess(): Unit = {
    when(
      mockLegacySubscriptionAuditService.auditSuccess(
        any[Arn],
        any[LegacyRegime],
        any[Option[String]]
      )
    ).thenReturn(Future.successful(()))
  }

  def mockLegacySubscriptionAuditFailure(): Unit = {
    when(
      mockLegacySubscriptionAuditService.auditFailure(
        any[Arn],
        any[LegacyRegime],
        any[String]()
      )
    ).thenReturn(Future.successful(()))

  }

}
