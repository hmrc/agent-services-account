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

package uk.gov.hmrc.agentservicesaccount.controllers

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.auth.AuthActions
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.services.SubscriptionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class LegacySubscriptionController @Inject() (
  legacySubscriptionService: SubscriptionService,
  cc: ControllerComponents,
  authActions: AuthActions,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
extends BackendController(cc)
with Logging:

  def startSubscription(regime: LegacyRegime): Action[AnyContent] = authActions.authorisedWithArnAndCredId {
    implicit request => arn => adminCredId => groupId =>
      request.body.asJson.map(_.validate[SubscriptionRequest](SubscriptionRequest.reads(regime))) match {
        case Some(JsSuccess(request: PayeSubscriptionRequest, _)) =>
          legacySubscriptionService.startPayeSubscription(arn, request, adminCredId, groupId).map(_ => Ok)
        case Some(JsSuccess(request: SaSubscriptionRequest, _)) => Future.successful(NotImplemented)
        case Some(JsSuccess(request: CtSubscriptionRequest, _)) => Future.successful(NotImplemented)
        case Some(JsError(errors)) => Future.successful(BadRequest(s"Invalid subscription request, reason: $errors"))
        case _ => Future.successful(BadRequest("Missing subscription request JSON"))
      }
  }

  def roboticsCallback(): Action[SubscriptionCallback] =
    Action.async(parse.json[SubscriptionCallback]) { implicit request =>
      val correlationId = request.headers.get("correlationId")

      correlationId match {
        case Some(id) =>
          legacySubscriptionService.handleRoboticsCallback(request.body, id).map {
            case true => NoContent
            case false =>
              val msg = s"Did not find a work item with correlationId: $id"
              logger.error(s"[roboticsCallback] $msg")
              NotFound(msg)
          }
        case None =>
          val msg = "Missing correlationId header in robotics callback request"
          logger.error(s"[roboticsCallback] $msg")
          Future.successful(BadRequest(msg))
      }
    }
