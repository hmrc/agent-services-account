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

package uk.gov.hmrc.agentservicesaccount.mocks

import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.connectors.CitizenDetailsConnector
import uk.gov.hmrc.agentservicesaccount.models.agententity.DeceasedCheckException
import uk.gov.hmrc.domain.SaUtr

import scala.concurrent.Future

trait MockCitizenDetailsConnector extends MockitoSugar {

  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  def mockGetCitizenDeceasedFlag(saUtr: SaUtr)(
    result: Option[DeceasedCheckException]
  )(implicit request: RequestHeader): Unit = {
    when(mockCitizenDetailsConnector.getCitizenDeceasedFlag(saUtr))
      .thenReturn(Future.successful(result))
  }

  def mockGetCitizenDeceasedFlagFailure(saUtr: SaUtr)(
    ex: Throwable
  )(implicit request: RequestHeader): Unit = {
    when(mockCitizenDetailsConnector.getCitizenDeceasedFlag(saUtr))
      .thenReturn(Future.failed(ex))
  }
}
