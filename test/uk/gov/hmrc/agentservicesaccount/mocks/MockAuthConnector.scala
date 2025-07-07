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
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{EmptyRetrieval, Retrieval}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait MockAuthConnector extends MockitoSugar { this: TestSuite =>

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuth()(response: Either[String, Enrolments]): Unit = {
    when(
      mockAuthConnector.authorise(
        meq(AuthProviders(GovernmentGateway)),
        meq(Retrievals.allEnrolments)
      )(any[HeaderCarrier], any[ExecutionContext])
    ).thenReturn(
      response.fold(
        e => Future.failed(new Exception(e)),
        r => Future.successful(r)
      )
    )
  }

  def mockAuthWithNoRetrievals[A](retrieval: Retrieval[A])(result: A): Unit = {
    when(
      mockAuthConnector.authorise[A](
        meq(EmptyPredicate),
        meq(retrieval)
      )(any[HeaderCarrier], any[ExecutionContext])
    ).thenReturn(Future.successful(result))
  }

  def mockAgentAuth()(response: Either[String, Unit]): Unit = {
    when(
      mockAuthConnector.authorise(
        meq(AuthProviders(GovernmentGateway).and(AffinityGroup.Agent)),
        meq(EmptyRetrieval)
      )(any[HeaderCarrier], any[ExecutionContext])
    ).thenReturn(
      response.fold(
        e => Future.failed(new Exception(e)),
        r => Future.successful(r)
      )
    )
  }
}
