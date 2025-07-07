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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.internalauth.client.test.StubBehaviour
import uk.gov.hmrc.internalauth.client.{Predicate, Retrieval}

import scala.concurrent.Future

trait MockInternalAuth extends MockitoSugar { this: TestSuite =>

  val mockStubBehaviour: StubBehaviour = mock[StubBehaviour]

  def mockInternalAuthSuccess(): Unit = {
    when(mockStubBehaviour.stubAuth[Unit](any[Option[Predicate]], any[Retrieval[Unit]]))
      .thenReturn(Future.unit)
  }

  def mockInternalAuthFailure(e: Throwable): Unit = {
    when(mockStubBehaviour.stubAuth[Unit](any[Option[Predicate]], any[Retrieval[Unit]]))
      .thenReturn(Future.failed(e))
  }
}
