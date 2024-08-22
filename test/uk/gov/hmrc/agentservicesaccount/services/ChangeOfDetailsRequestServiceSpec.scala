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

import java.time.Instant

import scala.concurrent.Future

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import org.bson.BsonObjectId
import org.mockito.Mockito.when
import play.api.test.Helpers.await
import uk.gov.hmrc.agentservicesaccount.assets.TestConstants.testChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.models.ChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.repositories.ChangeOfDetailsRequestRepository
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class ChangeOfDetailsRequestServiceSpec extends UnitSpec:

  val mockChangeOfDetailsRequestRepository: ChangeOfDetailsRequestRepository = mock[ChangeOfDetailsRequestRepository]
  val testService: ChangeOfDetailsRequestService = new ChangeOfDetailsRequestService(
    mockChangeOfDetailsRequestRepository
  )

  "find" should:
    "return Some(changeOfDetailsRequest)" when:
      "a matching record is available in the DB" in:
        when(mockChangeOfDetailsRequestRepository.find("AARN1234567"))
          .thenReturn(Future.successful(Some(testChangeOfDetailsRequest)))
        val result: Option[ChangeOfDetailsRequest] = await(testService.find("AARN1234567"))

        result shouldBe Some(testChangeOfDetailsRequest)

    "return None" when:
      "no matching record is available in the DB" in:
        when(mockChangeOfDetailsRequestRepository.find("AARN1234567")).thenReturn(Future.successful(None))
        val result: Option[ChangeOfDetailsRequest] = await(testService.find("AARN1234567"))

        result shouldBe None

  "upsert" should:
    "return UpdateResult and was acknowledged" when:
      "the record is successfully inserted in the DB" in:
        when(mockChangeOfDetailsRequestRepository.upsert(testChangeOfDetailsRequest))
          .thenReturn(Future.successful(UpdateResult.acknowledged(1, 1, BsonObjectId())))
        val result: UpdateResult = await(testService.upsert(testChangeOfDetailsRequest))

        result.wasAcknowledged() shouldBe true

    "return UpdateResult and was acknowledged" when:
      "the record is successfully updated in the DB" in:
        when(mockChangeOfDetailsRequestRepository.upsert(testChangeOfDetailsRequest))
          .thenReturn(Future.successful(UpdateResult.acknowledged(1, 1, BsonObjectId())))
        val result: UpdateResult = await(testService.upsert(testChangeOfDetailsRequest))

        result.wasAcknowledged() shouldBe true

    "return UpdateResult and was not acknowledged" when:
      "the record failed to update in the DB" in:
        when(mockChangeOfDetailsRequestRepository.upsert(testChangeOfDetailsRequest))
          .thenReturn(Future.successful(UpdateResult.unacknowledged()))
        val result: UpdateResult = await(testService.upsert(testChangeOfDetailsRequest))

        result.wasAcknowledged() shouldBe false

    "return UpdateResult and was not acknowledged" when:
      "the record failed to insert in the DB" in:
        when(mockChangeOfDetailsRequestRepository.upsert(testChangeOfDetailsRequest))
          .thenReturn(Future.successful(UpdateResult.unacknowledged()))
        val result: UpdateResult = await(testService.upsert(testChangeOfDetailsRequest))

        result.wasAcknowledged() shouldBe false

    "throw an exception" when:
      "mongodb fails to upsert the record" in:
        when(mockChangeOfDetailsRequestRepository.upsert(testChangeOfDetailsRequest))
          .thenReturn(Future.failed(new Exception("Failed to upsert ChangeOfDetailsRequest for ARN: AARN1234567")))

        intercept[Exception]:
          await(testService.upsert(testChangeOfDetailsRequest))

  "delete" should:
    "return DeleteResult and was acknowledged and deleted count is 1" when:
      "the record is successfully deleted from the DB" in:
        when(mockChangeOfDetailsRequestRepository.delete("AARN1234567"))
          .thenReturn(Future.successful(DeleteResult.acknowledged(1)))
        val result: DeleteResult = await(testService.delete("AARN1234567"))

        result shouldBe DeleteResult.acknowledged(1)

    "return DeleteResult and was acknowledged and deleted count is 0" when:
      "the record is not successfully deleted from the DB" in:
        when(mockChangeOfDetailsRequestRepository.delete("AARN1234567"))
          .thenReturn(Future.successful(DeleteResult.acknowledged(0)))
        val result: DeleteResult = await(testService.delete("AARN1234567"))

        result shouldBe DeleteResult.acknowledged(0)
