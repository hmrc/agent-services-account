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
import scala.concurrent.duration.*
import play.api.Configuration
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem
import uk.gov.hmrc.agentservicesaccount.models.subscription.TargetSystem.CESA
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Singleton
class AppConfig @Inject() (
  config: Configuration,
  servicesConfig: ServicesConfig
):

  val appName = "agent-services-account"

  val stubsCompatibilityMode: Boolean = config.get[Boolean]("stubs-compatibility-mode")

  val roboticsWorkflowMetaDataSolution: String = config.get[String]("robotics.request.requestData.workflowMetaData.solution")
  val roboticsWorkflowMetaDataWorkflowID: String = config.get[String]("robotics.request.requestData.workflowMetaData.workflowId")

  val mongoTtl: Long = config.get[Long]("mongodb.timeToLive")
  val enrolmentStoreProxyBaseUrl: String = baseUrl("enrolment-store-proxy")
  val citizenDetailsBaseUrl: String = baseUrl("citizen-details")
  val desBaseUrl: String = baseUrl("des")
  val desAuthToken: String = servicesConfig.getString("microservice.services.des.authorization-token")
  val desEnv: String = servicesConfig.getString("microservice.services.des.environment")

  val hipBaseUrl: String = servicesConfig.baseUrl("hip")
  val hipAuthToken: String = servicesConfig.getString("microservice.services.hip.authorization-token")
  val getAgentRecordViaHIP: Boolean = config.get[Boolean]("features.get-agent-record-via-hip")

  val automapLockExpires: Duration = servicesConfig.getDuration("agent.automap.lock.expires")
  val entityChecksLockExpires: Duration = servicesConfig.getDuration("agent.entity-check.lock.expires")
  val entityChecksEmailLockExpires: Duration = servicesConfig.getDuration("agent.entity-check.email.lock.expires")
  val emailBaseUrl: String = baseUrl("email")
  val agentAssuranceBaseUrl: String = baseUrl("agent-assurance")
  val agentMaintainerEmail: String = config.get[String]("agent-maintainer-email")
  val agentEpayeRegistrationBaseUrl: String = baseUrl("agent-epaye-registration")
  val agentMappingBaseUrl: String = baseUrl("agent-mapping")

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

  private def getWorkItemJobConfig(name: String): WorkItemJobConfig = WorkItemJobConfig(
    enabled = config.get[Boolean](s"work-item-jobs.$name.enabled"),
    schedulerDelay = config.get[Duration](s"work-item-jobs.$name.scheduler-delay").toMillis.millis,
    schedulerInterval = config.get[Duration](s"work-item-jobs.$name.scheduler-interval").toMillis.millis,
    retryInterval = config.get[Duration](s"work-item-jobs.$name.retry-interval").toMillis.millis,
    maxAttempts = config.get[Int](s"work-item-jobs.$name.max-attempts"),
    availableAt = config.getOptional[String](s"work-item-jobs.$name.available-at").map(LocalTime.parse)
  )
  val payeKnownFactsJobConfig: WorkItemJobConfig = getWorkItemJobConfig("paye-known-facts")

  val saKnownFactsJobConfig: WorkItemJobConfig = getWorkItemJobConfig("sa-known-facts")

  val ctKnownFactsJobConfig: WorkItemJobConfig = getWorkItemJobConfig("ct-known-facts")

  val saRoboticsJobConfig: WorkItemJobConfig = getWorkItemJobConfig("sa-robotics")

  val ctRoboticsJobConfig: WorkItemJobConfig = getWorkItemJobConfig("ct-robotics")

  def knownFactsAvailableAt(callbackTargetSystem: TargetSystem): Instant = {
    val availableAt: Option[LocalTime] =
      callbackTargetSystem match {
        case TargetSystem.CESA => saRoboticsJobConfig.availableAt
        case TargetSystem.COTAX => ctRoboticsJobConfig.availableAt
      }

    availableAt
      .map(LocalDateTime.of(LocalDate.now().plusDays(1), _))
      .map(_.atZone(ZoneId.of("Europe/London")).toInstant)
      .getOrElse(Instant.now())
  }

  private def baseUrl(key: String) = servicesConfig.baseUrl(key)
