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

import com.codahale.metrics.MetricRegistry
import play.api.Configuration
import play.api.libs.json.*
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.services.Cache
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.{CacheIdType, MongoCacheRepository}
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Success

@Singleton
class AgencyDetailsCacheRepository @Inject() (
  config: Configuration,
  mongo: MongoComponent,
  timestampSupport: TimestampSupport,
  metrics: Metrics
)(
  implicit
  ec: ExecutionContext,
  @Named("aes") crypto: Encrypter & Decrypter
)
//TODO WG - I have to use my custom EntityCache as is incorrectly implemented EntityCache
extends EntityCacheCustom[String, AgentDetailsDesResponse]
with Cache[AgentDetailsDesResponse] {

  override val format: Format[AgentDetailsDesResponse] = AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat(using crypto)

  override val cacheRepo: MongoCacheRepository[String] =
    new MongoCacheRepository(
      mongoComponent = mongo,
      collectionName = "cache-agent-details",
      ttl = Duration.create(config.underlying.getString("agent.entity.cache.expires")),
      timestampSupport = timestampSupport,
      cacheIdType = CacheIdType.SimpleCacheId,
      replaceIndexes = true,
      extraCodecs = Seq(
        Codecs.playFormatCodec[AgentDetailsDesResponse](AgentDetailsDesResponse.agentRecordDatabaseDetailsFormat(using crypto))
      )
    )

  val record: MetricRegistry = metrics.defaultRegistry

  def apply(
    key: String
  )(body: => Future[AgentDetailsDesResponse])(implicit ec: ExecutionContext): Future[AgentDetailsDesResponse] = {
    val encryptedKey = crypto.encrypt(PlainText(key)).value
    getFromCache(encryptedKey).flatMap {
      case Some(v) =>
        record.counter(s"Count-$key-from-cache")
        Future.successful(v)
      case None =>
        body.andThen {
          case Success(v) =>
            record.counter(s"Count-$key-from-source")
            putCache(encryptedKey)(v).map(_ => v)
        }
    }
  }

  override def delete(key: String)(using ec: ExecutionContext): Future[Unit] =
    val encryptedKey = crypto.encrypt(PlainText(key)).value
    deleteFromCache(encryptedKey)

}
