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

package uk.gov.hmrc.agentservicesaccount.controllers
import cats.data.EitherT
import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.*
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.auth.AuthActions
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.HipConnector
import uk.gov.hmrc.agentservicesaccount.controllers.RequestValidation.*
import uk.gov.hmrc.agentservicesaccount.models.AgentRecordUpdateRequest
import uk.gov.hmrc.agentservicesaccount.models.HipAmendPayload.toHipAmendPayload
import uk.gov.hmrc.agentservicesaccount.models.dms.DmsSubmissionReference
import uk.gov.hmrc.agentservicesaccount.services.{AgentDetailsService, DmsService}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AgentDetailsController @Inject() (
  cc: ControllerComponents,
  agentEntityService: AgentDetailsService,
  hipConnector: HipConnector,
  dmsService: DmsService,
  authActions: AuthActions,
  auth: BackendAuthComponents
)(implicit
  ec: ExecutionContext,
  appConfig: AppConfig
)
extends BackendController(cc)
with Logging {

  // for agents
  def agentGetWithChecks: Action[AnyContent] = authActions.authorisedWithArn { implicit request => arn =>
    agentEntityService
      .getAgentDetailsWithChecks(arn)
      .map(entityCheckResult => Ok(Json.toJson(entityCheckResult.agentRecord)))
  }

  // clients, stride
  def clientGetWithChecks(arn: Arn): Action[AnyContent] = internalAuth.async { implicit request =>
    agentEntityService
      .getAgentDetailsWithChecks(arn)
      .map(entityCheckResult => Ok(Json.toJson(entityCheckResult.agentRecord)))
  }

  def post(arn: Arn): Action[AnyContent] =
    authActions.withAffinityGroupAgentOrStride(strideRoles) {
      implicit request =>
        for {
          dmsResponse <- dmsService.submitToDms(
            request.body.asText,
            Instant.now().truncatedTo(ChronoUnit.SECONDS),
            DmsSubmissionReference.create
          )
        } yield {
          logger.info(
            s"Dms Submission successful for ${arn.value}: ${dmsResponse.reference} at ${dmsResponse.processingDate}"
          )
          Created
        }
    }

  def agentRecordUpdate: Action[AnyContent] = authActions.authorisedWithArn { request => arn =>
    given Request[AnyContent] = request
    (
      for
        json       <- request.body.toJsonEitherT
        rawRecord  <- json.validateEitherT[AgentRecordUpdateRequest]
        hipPayload <- rawRecord.toHipAmendPayload.toResultEitherT
        response   <- EitherT.liftF(hipConnector.putAgentRecord(arn, hipPayload))
      yield Ok(Json.obj("processingDate" -> response.success.processingDate))
    ).value.map {
      case Right(result) => result
      case Left(error)   => error
    }
  }

  private val strideRoles = Seq(appConfig.manuallyAssuredStrideRole)

  private val predicate = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType(appConfig.appName),
      resourceLocation = ResourceLocation("agent-record-with-checks/arn")
    ),
    action = IAAction("WRITE")
  )

  private val internalAuth = auth.authorizedAction(predicate)

}
