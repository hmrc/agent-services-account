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
import uk.gov.hmrc.agentservicesaccount.config.KnownFactsJobConfig

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class SaKnownFactsScheduler @Inject() (
  actorSystem: ActorSystem,
  jobConfig: KnownFactsJobConfig,
  worker: SaKnownFactsWorker,
  lifecycle: ApplicationLifecycle
)(using ec: ExecutionContext)
extends Logging {

  private val scheduled =
    actorSystem.scheduler.scheduleAtFixedRate(
      jobConfig.initialDelay,
      jobConfig.interval
    )(() =>
      worker.runOnce().recover {
        case error => logger.error("SA known facts scheduler failed", error)
      }
    )

  lifecycle.addStopHook(() => Future.successful(scheduled.cancel()))

}
