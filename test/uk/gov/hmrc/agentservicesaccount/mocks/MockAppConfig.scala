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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.*
import org.scalatest.TestSuite
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt

trait MockAppConfig extends MockitoSugar { this: TestSuite =>

  val mockServiceConfig: ServicesConfig = mock[ServicesConfig]
  val mockConfig: Configuration = mock[Configuration]

  // Stub Configuration
  when(mockConfig.get[String](meq("agent-maintainer-email"))(any()))
    .thenReturn("test@example.com")

  when(mockConfig.get[Long](meq("mongodb.timeToLive"))(any()))
    .thenReturn(3600L)

  // Stub ServicesConfig - getString
  when(mockServiceConfig.getString(meq("stride.roles.agent-services-account")))
    .thenReturn("maintain_agent_manually_assure")

  when(mockServiceConfig.getString(meq("internal-auth.token")))
    .thenReturn("YWdlbnQtYXNzdXJhbmNl")

  when(mockServiceConfig.getString(meq("microservice.services.dms-submission.contact-details-submission.callbackEndpoint")))
    .thenReturn("callbackEndpoint")

  when(mockServiceConfig.getString(meq("microservice.services.dms-submission.contact-details-submission.businessArea")))
    .thenReturn("businessArea")

  when(mockServiceConfig.getString(meq("microservice.services.dms-submission.contact-details-submission.customerId")))
    .thenReturn("customerId")

  when(mockServiceConfig.getString(meq("microservice.services.dms-submission.contact-details-submission.source")))
    .thenReturn("source")

  when(mockServiceConfig.getString(meq("microservice.services.des.authorization-token")))
    .thenReturn("test-des-token")

  when(mockServiceConfig.getString(meq("microservice.services.des.environment")))
    .thenReturn("test-env")

  // Stub ServicesConfig - getBoolean
  when(mockServiceConfig.getBoolean(meq("internal-auth-token-enabled-on-start")))
    .thenReturn(false)

  // Stub ServicesConfig - getDuration
  when(mockServiceConfig.getDuration(meq("agent.entity-check.lock.expires")))
    .thenReturn(1.second)

  when(mockServiceConfig.getDuration(meq("agent.entity-check.email.lock.expires")))
    .thenReturn(1.second)

  // Stub ServicesConfig - getInt
  when(mockServiceConfig.getInt(meq("rate-limiter.business-names.max-calls-per-second")))
    .thenReturn(10)

  when(mockServiceConfig.getInt(any[String]))
    .thenReturn(1)

  // Stub ServicesConfig - baseUrl
  when(mockServiceConfig.baseUrl(any[String]))
    .thenReturn("some-url")

  // Catch-all fallback
  when(mockServiceConfig.getString(any[String]))
    .thenReturn("other-string")

  val mockAppConfig: AppConfig = new AppConfig(mockConfig, mockServiceConfig)
}
