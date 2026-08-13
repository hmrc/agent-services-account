/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.mocks.MockAppConfig
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class MongoLockServiceSpec
extends UnitSpec
with CleanMongoCollectionSupport
with MockAppConfig {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(300, Millis)))

  val mongoLockRepository = new MongoLockRepository(mongoComponent, new CurrentTimestampSupport)
  implicit val ac: AppConfig = mockAppConfig

  val service = new MongoLockService(mongoLockRepository)

  val utr1 = Utr("1234567")
  val utr2 = Utr("1234567")

  def retry[T](n: Int)(block: => T): T = {
    Try(block) match {
      case Success(result) => result
      case Failure(_) if n > 1 =>
        Thread.sleep(500)
        retry(n - 1)(block)
      case Failure(e) => throw e
    }
  }

  "MongoLockServiceSpec" should {
    "return Some(value) when not locked" in {
      service.dailyLock(utr1)(Future.successful(())).futureValue shouldBe Some(())
    }
    "return None when locked" in {
      service.dailyLock(utr1)(Future.successful(())).futureValue shouldBe Some(())
      service.dailyLock(utr1)(Future.successful(())).futureValue shouldBe None
    }

    "return Some(value) after TTL 1 second when locked" in {
      service.dailyLock(utr1)(Future.successful(())).futureValue shouldBe Some(())
      retry(5) {
        service.dailyLock(utr1)(Future.successful(())).futureValue shouldBe None
      }
      retry(5) {
        service.dailyLock(utr1)(Future.successful(())).futureValue shouldBe Some(())
      }
    }

  }

}
