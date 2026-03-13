/*
 * Copyright 2023 HM Revenue & Customs
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

import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.lock.TimePeriodLockService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class MongoLockService @Inject() (mongoLockRepository: MongoLockRepository)(implicit
  appConfig: AppConfig,
  ec: ExecutionContext
) {

  def dailyLock[T](utr: Utr)(body: => Future[T]): Future[Option[T]] = {
    val lockService = TimePeriodLockService(
      mongoLockRepository,
      lockId = s"verify-utr-daily-${utr.value}",
      ttl = appConfig.entityChecksLockExpires
    )
    lockService.withRenewedLock(body)
  }

  def emailLock[T](utr: Utr)(body: => Future[T]): Future[Option[T]] = {
    val lockService = TimePeriodLockService(
      mongoLockRepository,
      lockId = s"verify-utr-email-${utr.value}",
      ttl = appConfig.entityChecksEmailLockExpires
    )
    lockService.withRenewedLock(body)
  }

  def automapLock[T](arn: Arn)(body: => Future[T]): Future[Option[T]] = {
    val lockService = TimePeriodLockService(
      mongoLockRepository,
      lockId = s"arn-automap-${arn.value}",
      ttl = appConfig.automapLockExpires
    )
    lockService.withRenewedLock(body)
  }

}
