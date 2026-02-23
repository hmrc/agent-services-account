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

package uk.gov.hmrc.agentservicesaccount.repositories

import play.api.Logger
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubscriptionWorkItemRepositoryEnsureIndexesSpec extends UnitSpec {

  given ExecutionContext = ExecutionContext.global
  private val testLogger = Logger("SubscriptionWorkItemRepositoryEnsureIndexesSpec")

  "dropLegacyUniqueArnIndexIfPresent" should {
    "fail when listing indexes fails (so startup fails fast)" in {
      val ex =
        SubscriptionWorkItemRepository
          .dropLegacyUniqueArnIndexIfPresent(
            listIndexNames = Future.failed(new RuntimeException("list boom")),
            dropIndex = _ => Future.unit,
            logger = testLogger
          )
          .failed
          .futureValue

      ex.getMessage.shouldBe("list boom")
    }

    "fail when dropping legacy uniqueArn fails (so startup fails fast)" in {
      val ex =
        SubscriptionWorkItemRepository
          .dropLegacyUniqueArnIndexIfPresent(
            listIndexNames = Future.successful(Seq("uniqueArn")),
            dropIndex = _ => Future.failed(new RuntimeException("drop boom")),
            logger = testLogger
          )
          .failed
          .futureValue

      ex.getMessage.shouldBe("drop boom")
    }

    "succeed when legacy uniqueArn is not present" in {
      SubscriptionWorkItemRepository
        .dropLegacyUniqueArnIndexIfPresent(
          listIndexNames = Future.successful(Seq("uniqueArnRegime")),
          dropIndex = _ => Future.failed(new RuntimeException("should not be called")),
          logger = testLogger
        )
        .futureValue
    }
  }
}

