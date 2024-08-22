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

package uk.gov.hmrc.agentservicesaccount.controllers

import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.agentservicesaccount.assets.TestConstants.{testChangeOfDetailsRequest, testTimeSubmitted}
import uk.gov.hmrc.agentservicesaccount.models.ChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.repositories.ChangeOfDetailsRequestRepository
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper

class ChangeOfDetailsRequestControllerISpec extends ComponentSpecHelper:

  lazy val repository: ChangeOfDetailsRequestRepository = app.injector.instanceOf[ChangeOfDetailsRequestRepository]

  override def beforeEach(): Unit =
    await(repository.collection.drop().head())
    super.beforeEach()

  "GET /change-of-details-request/:arn" should :
    "return 200 with the change of details request" when :
      "a matching record is available in the DB" in :
        await(repository.upsert(testChangeOfDetailsRequest))

        val response = get(s"/change-of-details-request/AARN1234567")

        response.status shouldBe 200
        Json.fromJson[ChangeOfDetailsRequest](response.json)(ChangeOfDetailsRequest.format).get shouldBe testChangeOfDetailsRequest

    "return 404" when :
      "no matching record is available in the DB" in :
        val response = get(s"/change-of-details-request/AARN1234567")

        response.status shouldBe 404

  "POST /change-of-details-request" should :
    "return 204" when :
      "the record is successfully inserted in the DB" in :
        val response = post("/change-of-details-request")(Json.obj("arn" -> "AARN1234567", "timeSubmitted" -> testTimeSubmitted))

        response.status shouldBe 204

      "the record is successfully updated in the DB" in :
        await(repository.upsert(testChangeOfDetailsRequest))

        val response = post("/change-of-details-request")(Json.obj("arn" -> "AARN1234567", "timeSubmitted" -> testTimeSubmitted.plusSeconds(30)))

        response.status shouldBe 204

  "DELETE /change-of-details-request/:arn" should :
    "return 204" when :
      "the record is successfully deleted from the DB" in :
        await(repository.upsert(testChangeOfDetailsRequest))

        val response = delete(s"/change-of-details-request/AARN1234567")

        response.status shouldBe 204

    "return 404" when :
      "no matching record is available in the DB" in :
        val response = delete(s"/change-of-details-request/AARN1234567")

        response.status shouldBe 404

