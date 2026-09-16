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

import org.apache.pekko.actor.ActorSystem
import uk.gov.hmrc.agentservicesaccount.utils.RequestAwareLogging
import play.api.inject.ApplicationLifecycle
import play.api.mvc.Request
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.config.WorkItemJobConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentRegime.CT
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentRegime.SA
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentRegime
import uk.gov.hmrc.agentservicesaccount.models.subscription.UsesRobotics
import uk.gov.hmrc.agentservicesaccount.services.KnownFactsWorker
import uk.gov.hmrc.agentservicesaccount.services.RoboticsWorker
import uk.gov.hmrc.agentservicesaccount.support.NoRequest

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class SubscriptionScheduler @Inject() (
  actorSystem: ActorSystem,
  knownFactsWorker: KnownFactsWorker,
  roboticsWorker: RoboticsWorker,
  appConfig: AppConfig,
  lifecycle: ApplicationLifecycle
)(using
  ec: ExecutionContext
)
extends RequestAwareLogging:

  private given Request[?] = NoRequest

  private val knownFactsWorkerConfigs: Map[AgentRegime, WorkItemJobConfig] = Map(
    PAYE -> appConfig.payeKnownFactsJobConfig,
    SA -> appConfig.saKnownFactsJobConfig,
    CT -> appConfig.ctKnownFactsJobConfig
  )

  private val roboticsWorkerConfigs: Map[AgentRegime & UsesRobotics, WorkItemJobConfig] = Map(
    SA -> appConfig.saRoboticsJobConfig,
    CT -> appConfig.ctRoboticsJobConfig
  )

  private val knownFactsScheduled = knownFactsWorkerConfigs.flatMap { case (regime, config) =>
    if config.enabled then
      Some(actorSystem.scheduler.scheduleAtFixedRate(
        initialDelay = config.schedulerDelay,
        interval = config.schedulerInterval
      )(() =>
        knownFactsWorker.runOnce(using config, regime).recover {
          case error => logger.error(s"[SubscriptionScheduler] ${regime.toString} known facts scheduler run failed", error)
        }
      ))
    else
      logger.warn(s"[SubscriptionScheduler] ${regime.toString} known facts scheduler disabled by config")
      None
  }

  private val roboticsScheduled = roboticsWorkerConfigs.flatMap { case (regime, config) =>
    if config.enabled then
      Some(actorSystem.scheduler.scheduleAtFixedRate(
        initialDelay = config.schedulerDelay,
        interval = config.schedulerInterval
      )(() =>
        roboticsWorker.runOnce(using config, regime).recover {
          case error => logger.error(s"[SubscriptionScheduler] ${regime.toString} robotics scheduler run failed", error)
        }
      ))
    else
      logger.warn(s"[SubscriptionScheduler] ${regime.toString} robotics scheduler disabled by config")
      None
  }

  (knownFactsScheduled ++ roboticsScheduled).foreach(worker => lifecycle.addStopHook(() => Future.successful(worker.cancel())))
