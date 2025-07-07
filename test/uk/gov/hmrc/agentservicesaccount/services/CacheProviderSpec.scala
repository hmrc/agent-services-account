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

package uk.gov.hmrc.agentservicesaccount.services

import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.agentservicesaccount.models.AgentDetailsDesResponse
import uk.gov.hmrc.agentservicesaccount.repositories.AgencyDetailsCacheRepository

class CacheProviderSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "CacheProvider" should {

    "use AgencyDetailsCacheRepository when caching is enabled" in {
      val mockCacheRepo = mock[AgencyDetailsCacheRepository]
      val config = Configuration("agent.entity.cache.enabled" -> true)

      val provider = new CacheProvider(mockCacheRepo, config)

      provider.cacheEnabled mustBe true
      provider.agentDetailsCache mustBe mockCacheRepo
    }

    "use DoNotCache when caching is disabled" in {
      val mockCacheRepo = mock[AgencyDetailsCacheRepository]
      val config = Configuration("agent.entity.cache.enabled" -> false)

      val provider = new CacheProvider(mockCacheRepo, config)

      provider.cacheEnabled mustBe false
      provider.agentDetailsCache mustBe a[DoNotCache[_]]
    }

  }
}
