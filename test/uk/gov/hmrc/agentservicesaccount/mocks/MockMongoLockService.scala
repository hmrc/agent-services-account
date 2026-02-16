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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentservicesaccount.services.MongoLockService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait MockMongoLockService
extends MockitoSugar {
  this: TestSuite =>

  val mongoLockService: MongoLockService = mock[MongoLockService]

  def mockEmailLockSucceeds()(using ec: ExecutionContext): Unit = {
    when(mongoLockService.emailLock(any[Utr])(any()))
      .thenAnswer(new Answer[Future[Option[Unit]]] {
        override def answer(invocation: InvocationOnMock): Future[Option[Unit]] = {
          // Safely cast second argument as a by-name function
          val block = invocation.getArgument(1, classOf[Function0[Future[Unit]]])
          block().map(Some(_))
        }
      })
  }

  def mockDailyLockSucceeds[T](result: T)(using ec: ExecutionContext): Unit = {
    when(mongoLockService.dailyLock(any[Utr])(any()))
      .thenAnswer(new Answer[Future[Option[T]]] {
        override def answer(invocation: InvocationOnMock): Future[Option[T]] = {
          val block = invocation.getArgument(1, classOf[Function0[Future[T]]])
          block().map(Some(_))
        }
      })
  }

}
