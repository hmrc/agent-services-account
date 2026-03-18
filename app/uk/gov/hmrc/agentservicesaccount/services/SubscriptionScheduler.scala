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
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.CT
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.PAYE
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime.SA

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class SubscriptionScheduler @Inject() (
  actorSystem: ActorSystem,
  knownFactsWorker: KnownFactsWorker,
  saRoboticsWorker: SaRoboticsWorker,
  appConfig: AppConfig,
  lifecycle: ApplicationLifecycle
)(using
  ec: ExecutionContext
)
extends Logging:

  private val payeKnownFactsScheduled =
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay = appConfig.payeKnownFactsJobConfig.initialDelay,
      interval = appConfig.payeKnownFactsJobConfig.interval
    )(() =>
      knownFactsWorker.runOnce(using appConfig.payeKnownFactsJobConfig, PAYE).recover {
        case error => logger.error("[SubscriptionScheduler] PAYE known facts scheduler run failed", error)
      }
    )

  private val saKnownFactsScheduled =
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay = appConfig.saKnownFactsJobConfig.initialDelay,
      interval = appConfig.saKnownFactsJobConfig.interval
    )(() =>
      knownFactsWorker.runOnce(using appConfig.saKnownFactsJobConfig, SA).recover {
        case error => logger.error("[SubscriptionScheduler] SA known facts scheduler failed", error)
      }
    )

  private val ctKnownFactsScheduled =
    actorSystem.scheduler.scheduleAtFixedRate(
      initialDelay = appConfig.ctKnownFactsJobConfig.initialDelay,
      interval = appConfig.ctKnownFactsJobConfig.interval
    )(() =>
      knownFactsWorker.runOnce(using appConfig.ctKnownFactsJobConfig, CT).recover {
        case error => logger.error("[SubscriptionScheduler] CT known facts scheduler failed", error)
      }
    )

  private val saRoboticsScheduled =
    if appConfig.saRoboticsJobConfig.enabled then
      val s =
        actorSystem.scheduler.scheduleAtFixedRate(
          initialDelay = appConfig.saRoboticsJobConfig.initialDelay,
          interval = appConfig.saRoboticsJobConfig.interval
        )(() =>
          saRoboticsWorker.runOnce().recover {
            case error => logger.error("[SubscriptionScheduler] SA robotics scheduler run failed", error)
          }
        )
      Some(s)
    else
      logger.warn("[SubscriptionScheduler] SA robotics scheduler disabled by config")
      None

  private val ctRoboticsScheduled =
    if appConfig.ctRoboticsJobConfig.enabled then
      val s =
        actorSystem.scheduler.scheduleAtFixedRate(
          initialDelay = appConfig.ctRoboticsJobConfig.initialDelay,
          interval = appConfig.ctRoboticsJobConfig.interval
        )(() =>
          saRoboticsWorker.runOnce().recover {
            case error => logger.error("[SubscriptionScheduler] CT robotics scheduler run failed", error)
          }
        )
      Some(s)
    else
      logger.warn("[SubscriptionScheduler] CT robotics scheduler disabled by config")
      None

  lifecycle.addStopHook(() => Future.successful(payeKnownFactsScheduled.cancel()))
  lifecycle.addStopHook(() => Future.successful(saKnownFactsScheduled.cancel()))
  lifecycle.addStopHook(() => Future.successful(ctKnownFactsScheduled.cancel()))
  lifecycle.addStopHook(() => Future.successful(saRoboticsScheduled.foreach(_.cancel())))
  lifecycle.addStopHook(() => Future.successful(ctRoboticsScheduled.foreach(_.cancel())))
