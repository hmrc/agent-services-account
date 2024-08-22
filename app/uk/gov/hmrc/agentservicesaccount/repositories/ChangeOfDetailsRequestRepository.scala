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

package uk.gov.hmrc.agentservicesaccount.repositories

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes
import org.mongodb.scala.model.ReplaceOptions
import org.mongodb.scala.result.DeleteResult
import org.mongodb.scala.result.UpdateResult
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.ChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.repositories.ChangeOfDetailsRequestRepository.indexes
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.MongoComponent

@Singleton
class ChangeOfDetailsRequestRepository @Inject() (mongoComponent: MongoComponent)(
    using appConfig: AppConfig,
    ec: ExecutionContext
) extends PlayMongoRepository[ChangeOfDetailsRequest](
      collectionName = "change-of-details-request",
      domainFormat = ChangeOfDetailsRequest.mongoFormat,
      mongoComponent = mongoComponent,
      indexes = indexes(appConfig.mongoTtl),
      replaceIndexes = true
    ):

  def find(arn: String): Future[Option[ChangeOfDetailsRequest]] =
    collection.find(equal("arn", arn)).headOption()

  def upsert(changeOfDetailsRequest: ChangeOfDetailsRequest): Future[UpdateResult] =
    collection
      .replaceOne(
        filter = equal("arn", changeOfDetailsRequest.arn),
        replacement = changeOfDetailsRequest,
        options = new ReplaceOptions().upsert(true)
      )
      .head()

  def delete(arn: String): Future[DeleteResult] =
    collection.deleteOne(equal("arn", arn)).head()

object ChangeOfDetailsRequestRepository:
  def indexes(ttl: Long): Seq[IndexModel] = Seq(
    IndexModel(Indexes.ascending("arn"), new IndexOptions().unique(true)),
    IndexModel(
      Indexes.ascending("timeSubmitted"),
      new IndexOptions().name("expireAfterSeconds").expireAfter(ttl, TimeUnit.SECONDS)
    )
  )
