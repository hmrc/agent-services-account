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

package uk.gov.hmrc.agentservicesaccount.repositories

import com.mongodb.client.result.DeleteResult
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.*
import uk.gov.hmrc.agentservicesaccount.assets.TestConstants.testChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.models.ChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class ChangeOfDetailsRepositoryISpec extends DefaultPlayMongoRepositorySupport[ChangeOfDetailsRequest] with ComponentSpecHelper:

  val repository: ChangeOfDetailsRequestRepository = app.injector.instanceOf[ChangeOfDetailsRequestRepository]

  override def checkTtlIndex: Boolean = false

  override def beforeEach(): Unit =
    await(repository.collection.drop().head())
    super.beforeEach()

  "find" should :
    "return Some(changeOfDetailsRequest)" when :
      "a matching record is available in the DB" in :
        await(repository.collection.insertOne(testChangeOfDetailsRequest).head())

        val result = await(repository.find("AARN1234567"))
        result shouldBe Some(testChangeOfDetailsRequest)

    "return None" when :
      "no matching record is available in the DB" in :
        val result = await(repository.find("AARN1234567"))
        result shouldBe None

  "upsert" should :
    "return UpdateResult" when :
      "the record is successfully inserted in the DB" in :
        val result = await(repository.upsert(testChangeOfDetailsRequest))
        result.wasAcknowledged() shouldBe true

      "the record is successfully updated in the DB" in :
        await(repository.collection.insertOne(testChangeOfDetailsRequest).head())

        val updatedChangeOfDetailsRequest = testChangeOfDetailsRequest.copy(timeSubmitted = testChangeOfDetailsRequest.timeSubmitted.plusSeconds(30))
        val result = await(repository.upsert(updatedChangeOfDetailsRequest))
        result.wasAcknowledged() shouldBe true

        val updatedResult = await(repository.find("AARN1234567"))
        updatedResult shouldBe Some(updatedChangeOfDetailsRequest)

  "delete" should :
    "return DeleteResult with a count of 1" when :
      "a matching record is available in the DB" in :
        await(repository.collection.insertOne(testChangeOfDetailsRequest).head())

        val result = await(repository.delete("AARN1234567"))
        result shouldBe DeleteResult.acknowledged(1)

    "return DeleteResult with a count of 0" when :
      "no matching record is available in the DB" in :
        val result = await(repository.delete("AARN1234567"))
        result shouldBe DeleteResult.acknowledged(0)

  // TODO test indexing?
