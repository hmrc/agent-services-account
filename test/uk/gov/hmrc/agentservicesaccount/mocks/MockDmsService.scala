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

package uk.gov.hmrc.agentservicesaccount.mocks

import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.models.dms.DmsResponse
import uk.gov.hmrc.agentservicesaccount.models.dms.DmsSubmissionReference
import uk.gov.hmrc.agentservicesaccount.services.DmsService

import java.time.Instant
import scala.concurrent.Future

trait MockDmsService
extends MockFactory { this: TestSuite =>

  val mockDmsService = mock[DmsService]

  def mockSubmitToDmsSuccess =
    (mockDmsService
      .submitToDms(
        _: Option[String],
        _: Instant,
        _: DmsSubmissionReference
      )(using _: RequestHeader))
      .expects(*, *, *, *)
      .returning(Future.successful(DmsResponse(Instant.now(), "")))

}
