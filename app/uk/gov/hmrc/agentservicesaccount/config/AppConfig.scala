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

package uk.gov.hmrc.agentservicesaccount.config

import javax.inject.Inject
import javax.inject.Singleton
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.Duration
import scala.util.matching.Regex

@Singleton
class AppConfig @Inject() (
                            config: Configuration,
                            servicesConfig: ServicesConfig):

  val appName = "agent-services-account"
  
  val mongoTtl: Long = config.get[Long]("mongodb.timeToLive")
  val citizenDetailsBaseUrl: String = baseUrl("citizen-details")
  val desBaseUrl: String = baseUrl("des")
  val desAuthToken: String = servicesConfig.getString("microservice.services.des.authorization-token")
  val desEnv: String = servicesConfig.getString("microservice.services.des.environment")

  val internalHostPatterns: Seq[Regex] = config.get[Seq[String]]("internalServiceHostPatterns").map(_.r)
  val entityChecksLockExpires: Duration = servicesConfig.getDuration("agent.entity-check.lock.expires")
  val entityChecksEmailLockExpires: Duration = servicesConfig.getDuration("agent.entity-check.email.lock.expires")
  val emailBaseUrl: String = baseUrl("email")
  val agentAssuranceBaseUrl: String = baseUrl("agent-assurance")
  val agentMaintainerEmail: String = config.get[String]("agent-maintainer-email")

  val internalAuthBaseUrl: String = servicesConfig.baseUrl("internal-auth")
  val internalAuthToken: String = servicesConfig.getString("internal-auth.token")
  val internalAuthTokenEnabled: Boolean = servicesConfig.getBoolean("internal-auth-token-enabled-on-start")

  val manuallyAssuredStrideRole: String = servicesConfig.getString("stride.roles.agent-services-account")


  private val dmsBaseUrl: String = servicesConfig.baseUrl("dms-submission")
  private val appBaseUrl: String = servicesConfig.baseUrl("self")
  private val dmsSubmissionCallbackEndpoint: String = servicesConfig.getString(
    "microservice.services.dms-submission.contact-details-submission.callbackEndpoint"
  )

  val dmsSubmissionBusinessArea: String = servicesConfig.getString("microservice.services.dms-submission.contact-details-submission.businessArea")
  val dmsSubmissionCallbackUrl: String = s"$appBaseUrl/$appName/$dmsSubmissionCallbackEndpoint"
  val dmsSubmissionClassificationType: String = servicesConfig.getString("microservice.services.dms-submission.contact-details-submission.classificationType")
  val dmsSubmissionCustomerId: String = servicesConfig.getString("microservice.services.dms-submission.contact-details-submission.customerId")
  val dmsSubmissionFormId: String = servicesConfig.getString("microservice.services.dms-submission.contact-details-submission.formId")
  val dmsSubmissionSource: String = servicesConfig.getString("microservice.services.dms-submission.contact-details-submission.source")
  val dmsSubmissionUrl: String = dmsBaseUrl + "/dms-submission/submit"


  private def baseUrl(key: String) = servicesConfig.baseUrl(key)
