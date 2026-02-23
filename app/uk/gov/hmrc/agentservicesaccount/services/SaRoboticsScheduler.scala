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

import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.agentservicesaccount.config.RoboticsJobConfig

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class SaRoboticsScheduler @Inject() (
  actorSystem: ActorSystem,
  jobConfig: RoboticsJobConfig,
  worker: SaRoboticsWorker,
  lifecycle: ApplicationLifecycle
)(using
  ec: ExecutionContext
)
extends Logging:

  private val scheduled =
    if jobConfig.enabled then
      val s =
        actorSystem.scheduler.scheduleAtFixedRate(
          initialDelay = jobConfig.initialDelay,
          interval = jobConfig.interval
        )(() => worker.runOnce().recover { case error => logger.error("SA robotics scheduler run failed", error) })
      Some(s)
    else
      logger.warn("SA robotics scheduler disabled by config")
      None

  lifecycle.addStopHook(() => Future.successful(scheduled.foreach(_.cancel())))

