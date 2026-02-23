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

package uk.gov.hmrc.agentservicesaccount.models.subscription

import play.api.libs.json.Format
import uk.gov.hmrc.agentservicesaccount.utils.EnumFormat
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*

enum SubscriptionStatus:
  case SubscriptionInProgress, SubscriptionFailed, SubscriptionMapped, SubscriptionOnAgency, NotSubscribed, InvalidStatus

object SubscriptionStatus:

  given Format[SubscriptionStatus] = EnumFormat.enumFormat

  def fromProcessingStatus(processingStatus: ProcessingStatus): SubscriptionStatus =
    processingStatus match {
      case ToDo | InProgress | Failed | Deferred => SubscriptionInProgress // Failed/Deferred included as those imply retryable
      case PermanentlyFailed => SubscriptionFailed
      case _ => InvalidStatus // Other status types should never be set and completion should remove the work item
    }
