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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.auth.AuthActions
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.services.AgentEntityService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AgentEntityController @Inject()(
                                       cc: ControllerComponents,
                                       agentEntityService: AgentEntityService,
                                       val authConnector: AuthConnector,
                                       auth: BackendAuthComponents
)(implicit
  ec: ExecutionContext,
  appConfig: AppConfig
)
extends BackendController(cc) 
  with AuthActions {

  //for agents
  def agentVerifyEntity: Action[AnyContent] = AuthorisedWithArn { implicit request => arn =>
    agentEntityService
      .verifyAgent(arn)
      .map(entityCheckResult => Ok(Json.toJson(entityCheckResult.agentRecord)))
  }

  //clients, stride
  def clientVerifyEntity(arn:Arn): Action[AnyContent] = internalAuth.async  { implicit request =>
    agentEntityService
      .verifyAgent(arn)
      .map(entityCheckResult => Ok(Json.toJson(entityCheckResult.agentRecord)))
    }


  private val predicate = Predicate.Permission(
    resource = Resource(
      resourceType = ResourceType(appConfig.appName),
      resourceLocation = ResourceLocation("agent-record-with-checks/arn")
    ),
    action = IAAction("WRITE")
  )

  val internalAuth = auth.authorizedAction(predicate)


}
