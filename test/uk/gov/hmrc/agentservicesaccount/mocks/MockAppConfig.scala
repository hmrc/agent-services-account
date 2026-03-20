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

// Add these:

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as meq
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait MockAppConfig
extends MockitoSugar { this: TestSuite =>

  val mockServiceConfig: ServicesConfig = mock[ServicesConfig]
  val mockConfig: Configuration = mock[Configuration]

  stubCommonConfig(mockConfig, mockServiceConfig)
  val mockAppConfig: AppConfig = new AppConfig(mockConfig, mockServiceConfig)

  val mockConfigWithHip: Configuration = mock[Configuration]
  val mockServiceConfigHip: ServicesConfig = mock[ServicesConfig]
  val mockConfigHip: Configuration = mock[Configuration]
  stubCommonConfig(mockConfigHip, mockServiceConfigHip)
  when(mockConfigHip.get[Boolean](meq("features.get-agent-record-via-hip"))(any()))
    .thenReturn(true)
  val mockAppConfigHip: AppConfig = new AppConfig(mockConfigHip, mockServiceConfigHip)

  def stubCommonConfig(
    mockConfig: Configuration,
    mockServiceConfig: ServicesConfig
  ): Unit = {
    import org.mockito.ArgumentMatchers.any
    import org.mockito.ArgumentMatchers.eq as meq
    import org.mockito.Mockito.when
    import scala.concurrent.duration._

    when(mockConfig.get[String](meq("agent-maintainer-email"))(any()))
      .thenReturn("test@example.com")

    when(mockConfig.get[Long](meq("mongodb.timeToLive"))(any()))
      .thenReturn(3600L)

    when(mockConfig.get[Duration](meq("work-item-jobs.paye-known-facts.initial-delay"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.paye-known-facts.interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.paye-known-facts.retry-interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Int](meq("work-item-jobs.paye-known-facts.max-attempts"))(any()))
      .thenReturn(3)

    when(mockConfig.get[Duration](meq("work-item-jobs.sa-known-facts.initial-delay"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.sa-known-facts.interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.sa-known-facts.retry-interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Int](meq("work-item-jobs.sa-known-facts.max-attempts"))(any()))
      .thenReturn(3)

    when(mockConfig.get[Boolean](meq("stubs-compatibility-mode"))(any()))
      .thenReturn(false)

    when(mockConfig.get[Boolean](meq("work-item-jobs.sa-robotics.enabled"))(any()))
      .thenReturn(false)

    when(mockConfig.get[scala.concurrent.duration.Duration](meq("work-item-jobs.sa-robotics.initial-delay"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[scala.concurrent.duration.Duration](meq("work-item-jobs.sa-robotics.interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.ct-known-facts.initial-delay"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.ct-known-facts.interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Duration](meq("work-item-jobs.ct-known-facts.retry-interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Int](meq("work-item-jobs.ct-known-facts.max-attempts"))(any()))
      .thenReturn(3)

    when(mockConfig.get[Boolean](meq("work-item-jobs.ct-robotics.enabled"))(any()))
      .thenReturn(false)

    when(mockConfig.get[scala.concurrent.duration.Duration](meq("work-item-jobs.ct-robotics.initial-delay"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[scala.concurrent.duration.Duration](meq("work-item-jobs.ct-robotics.interval"))(any()))
      .thenReturn(1.second)

    when(mockConfig.get[Boolean](meq("features.get-agent-record-via-hip"))(any()))
      .thenReturn(false)

    when(mockServiceConfig.getString(meq("stride.roles.agent-services-account")))
      .thenReturn("maintain_agent_manually_assure")

    when(mockServiceConfig.getString(meq("internal-auth.token")))
      .thenReturn("YWdlbnQtYXNzdXJhbmNl")

    when(mockServiceConfig.getString(meq("microservice.services.des.authorization-token")))
      .thenReturn("test-des-token")

    when(mockServiceConfig.getString(meq("microservice.services.des.environment")))
      .thenReturn("test-env")

    when(mockServiceConfig.getDuration(meq("agent.automap.lock.expires")))
      .thenReturn(1.second)

    when(mockServiceConfig.getBoolean(meq("internal-auth-token-enabled-on-start")))
      .thenReturn(false)

    when(mockServiceConfig.getDuration(meq("agent.entity-check.lock.expires")))
      .thenReturn(1.second)

    when(mockServiceConfig.getDuration(meq("agent.entity-check.email.lock.expires")))
      .thenReturn(1.second)

    when(mockServiceConfig.getInt(meq("rate-limiter.business-names.max-calls-per-second")))
      .thenReturn(10)

    when(mockServiceConfig.getInt(any[String]))
      .thenReturn(1)

    when(mockServiceConfig.baseUrl(any[String]))
      .thenReturn("some-url")

    when(mockServiceConfig.getString(any[String]))
      .thenReturn("other-string")

  }

}
