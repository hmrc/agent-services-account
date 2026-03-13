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

import uk.gov.hmrc.agentservicesaccount.config.KnownFactsJobConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class PayeKnownFactsWorker @Inject() (
  workItemService: KnownFactsWorkItemService,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  jobConfig: KnownFactsJobConfig
)(using ec: ExecutionContext)
extends KnownFactsWorker(
  regime = LegacyRegime.PAYE,
  workItemService,
  enrolmentStoreProxyConnector,
  jobConfig
)
