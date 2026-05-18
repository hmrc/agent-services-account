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

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.extension.quartz.QuartzSchedulerExtension
import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.services.OrphanedWorkItemCleanupService

import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class OrphanedWorkItemCleanupScheduler @Inject() (
  actorSystem: ActorSystem,
  cleanupService: OrphanedWorkItemCleanupService,
  appConfig: AppConfig
)(using ec: ExecutionContext)
extends Logging {

  if (appConfig.orphanedWorkItemCleanupEnabled) {

    logger.info("[OrphanedWorkItemCleanupScheduler] Scheduler enabled")

    val scheduler = QuartzSchedulerExtension(actorSystem)

    val actorRef: ActorRef = actorSystem.actorOf(
      Props(
        new OrphanedWorkItemCleanupActor(cleanupService)
      )
    )

    scheduler.createJobSchedule(
      name = "OrphanedWorkItemCleanupSchedule",
      description = Some("Cleanup orphaned subscription work items"),
      cronExpression = appConfig.orphanedWorkItemCleanupCron,
      timezone = TimeZone.getTimeZone("Europe/London"),
      receiver = actorRef,
      msg = "<start>"
    )
  }
}
