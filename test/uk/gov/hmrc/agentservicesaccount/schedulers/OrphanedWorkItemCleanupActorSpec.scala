/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentservicesaccount.schedulers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestActorRef
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import uk.gov.hmrc.agentservicesaccount.mocks.MockAuditService
import uk.gov.hmrc.agentservicesaccount.services.OrphanedWorkItemCleanupService
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class OrphanedWorkItemCleanupActorSpec
extends UnitSpec
with IntegrationPatience
with CleanMongoCollectionSupport
with MockAuditService
with BeforeAndAfterEach
with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val actorSystem: ActorSystem = ActorSystem("OrphanedWorkItemCleanupActorSpec")

  override def afterAll(): Unit = {
    actorSystem.terminate()
    super.afterAll()
  }

  "receive" should {

    "trigger cleanup" in {

      val service = mock[OrphanedWorkItemCleanupService]

      when(service.cleanup())
        .thenReturn(Future.successful(()))

      val actor = TestActorRef(
        new OrphanedWorkItemCleanupActor(service)
      )

      actor ! "start"

      verify(service).cleanup()
    }
  }

}
