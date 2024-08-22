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

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers.await
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.agentservicesaccount.assets.TestConstants.testChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.services.ChangeOfDetailsRequestService
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.InternalServerException

class ChangeOfDetailsRequestControllerSpec extends UnitSpec:

  "upsert" should:
    "throw an InternalServerException" when:
      "mongo throws an exception" in:
        val mockService: ChangeOfDetailsRequestService = mock[ChangeOfDetailsRequestService]
        val controller: ChangeOfDetailsRequestController =
          new ChangeOfDetailsRequestController(mockService, stubControllerComponents())(using global)

        when(mockService.upsert(ArgumentMatchers.eq(testChangeOfDetailsRequest)))
          .thenReturn(Future.failed(new RuntimeException("Mongo exception")))

        intercept[InternalServerException]:
          await(controller.upsert()(FakeRequest().withBody(testChangeOfDetailsRequest)))

        verify(mockService).upsert(ArgumentMatchers.eq(testChangeOfDetailsRequest))
        // NOTE: this test was not possible at integration test level
        // it serves to prove that the controller is correctly handling the exception thrown by the service when mongo fails to write
