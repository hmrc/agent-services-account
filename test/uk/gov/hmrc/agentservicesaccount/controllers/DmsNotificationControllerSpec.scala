/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.agentservicesaccount.mocks.MockAppConfig
import uk.gov.hmrc.agentservicesaccount.models.dms.DmsNotification
import uk.gov.hmrc.agentservicesaccount.models.dms.SubmissionItemStatus
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.internalauth.client.test.BackendAuthComponentsStub
import uk.gov.hmrc.internalauth.client.test.StubBehaviour
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.internalauth.client.Predicate
import uk.gov.hmrc.internalauth.client.Retrieval

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class DmsNotificationControllerSpec
extends UnitSpec
with MockitoSugar
with MockAppConfig {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val mockStubBehaviour: StubBehaviour = mock[StubBehaviour]
  val stubBackendAuthComponents: BackendAuthComponents = BackendAuthComponentsStub(mockStubBehaviour)(using stubControllerComponents(), ec)

  val controller =
    new DmsNotificationController(
      stubControllerComponents(),
      stubBackendAuthComponents,
      mockAppConfig
    )

  val dmsNotification: DmsNotification = DmsNotification(
    id = "123",
    status = SubmissionItemStatus.Submitted,
    failureReason = None
  )

  "dmsCallback" should {
    "return OK" when {
      "when receiving a correct notification from DMS" in {
        when(
          mockStubBehaviour.stubAuth[Unit](
            org.mockito.ArgumentMatchers.any[Option[Predicate]],
            org.mockito.ArgumentMatchers.any[Retrieval[Unit]]
          )
        ).thenReturn(Future.unit)

        val request = FakeRequest(POST, routes.DmsNotificationController.dmsCallback().url)
          .withHeaders(HeaderNames.authorisation -> "Some auth token")
          .withBody(Json.toJson(dmsNotification))

        val result = controller.dmsCallback()(request)
        status(result) shouldBe OK
      }
    }

    "return BAD_REQUEST" when {
      "when an invalid request is received" in {
        when(
          mockStubBehaviour.stubAuth[Unit](
            org.mockito.ArgumentMatchers.any[Option[Predicate]],
            org.mockito.ArgumentMatchers.any[Retrieval[Unit]]
          )
        ).thenReturn(Future.unit)

        val request = FakeRequest(POST, routes.DmsNotificationController.dmsCallback().url)
          .withHeaders(HeaderNames.authorisation -> "Some auth token")
          .withBody(Json.obj())

        val result = controller.dmsCallback()(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "fail" when {
      "for an unauthenticated user" in {
        val request = FakeRequest(POST, routes.DmsNotificationController.dmsCallback().url)
          .withBody(Json.toJson(dmsNotification))

        val result = controller.dmsCallback()(request)
        Try(status(result)) match {
          case Success(_) => fail()
          case Failure(_) => succeed
        }
      }

      "when the user is not authorised" in {
        when(
          mockStubBehaviour.stubAuth[Unit](
            org.mockito.ArgumentMatchers.any[Option[Predicate]],
            org.mockito.ArgumentMatchers.any[Retrieval[Unit]]
          )
        ).thenReturn(Future.failed(new RuntimeException("Unauthorized")))

        val request = FakeRequest(POST, routes.DmsNotificationController.dmsCallback().url)
          .withHeaders(HeaderNames.authorisation -> "Some auth token")
          .withBody(Json.toJson(dmsNotification))

        val result = controller.dmsCallback()(request)
        Try(status(result)) match {
          case Success(_) => fail()
          case Failure(_) => succeed
        }
      }
    }
  }

}
