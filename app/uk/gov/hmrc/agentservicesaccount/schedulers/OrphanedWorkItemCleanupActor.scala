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

package uk.gov.hmrc.agentservicesaccount.schedulers

import org.apache.pekko.actor.Actor
import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.services.OrphanedWorkItemCleanupService

import scala.concurrent.ExecutionContext

class OrphanedWorkItemCleanupActor(
  cleanupService: OrphanedWorkItemCleanupService
)(using ec: ExecutionContext)
extends Actor
with Logging {

  override def receive: Receive = {
    case _ =>
      logger.info("[OrphanedWorkItemCleanupActor] Running cleanup")

      cleanupService.cleanup().recover {
        case e =>
          logger.error(
            "[OrphanedWorkItemCleanupActor] Cleanup failed",
            e
          )
      }

      ()
  }
}
