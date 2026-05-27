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
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.RequestHeader
import play.api.Logging
import uk.gov.hmrc.agentservicesaccount.auth.AuthActions
import uk.gov.hmrc.agentservicesaccount.models.subscription.*
import uk.gov.hmrc.agentservicesaccount.models.subscription.CallbackStatus.CallbackSuccess
import uk.gov.hmrc.agentservicesaccount.models.subscription.SubscriptionInfo.format
import uk.gov.hmrc.agentservicesaccount.services.SubscriptionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class LegacySubscriptionController @Inject() (
  legacySubscriptionService: SubscriptionService,
  cc: ControllerComponents,
  authActions: AuthActions
)(using ec: ExecutionContext)
extends BackendController(cc)
with Logging:

  def startSubscription(regime: LegacyRegime): Action[AnyContent] = authActions.authorisedWithArnAndCredId {
    request => arn => adminCredId => groupId =>
      given RequestHeader = request

      request.body.asJson.map(_.validate[SubscriptionRequest](using SubscriptionRequest.requestReads(regime))) match {
        case Some(JsSuccess(request: SubscriptionRequest, _)) =>
          legacySubscriptionService.startSubscriptionProcess(
            arn,
            request,
            regime,
            adminCredId,
            groupId
          ).map(_ => Ok)
        case Some(JsError(errors)) => Future.successful(BadRequest(s"Invalid subscription request, reason: ${formatErrors(errors)}"))
        case _ => Future.successful(BadRequest("Missing subscription request JSON"))
      }
  }

  private def formatErrors(errors: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]): String = errors
    .flatMap(_._2)
    .flatMap(_.messages)
    .distinct
    .mkString(", ")

  def subscriptionInfo(regimes: Seq[LegacyRegime]): Action[AnyContent] = authActions.authorisedWithArnAndGroupId {
    request => (arn, groupId) =>
      given RequestHeader = request
      legacySubscriptionService.getSubscriptionInfo(
        arn = arn,
        groupId = groupId,
        regimes = regimes
      ).map(subscriptionInfo => Ok(Json.toJson(subscriptionInfo)))
  }

  def roboticsCallback(): Action[SubscriptionCallback] =
    Action.async(parse.json[SubscriptionCallback]) { request =>
      if request.body.agentId.isEmpty && request.body.status == CallbackSuccess then
        val msg = "Missing agentId in 'success' callback payload"
        logger.error(s"[roboticsCallback] $msg")
        Future.successful(BadRequest(msg))
      else
        legacySubscriptionService.handleRoboticsCallback(request.body).map {
          case SubscriptionService.CallbackHandling.Handled => NoContent
          case SubscriptionService.CallbackHandling.NotFound =>
            val msg = s"Did not find a work item with requestId: ${request.body.requestId}"
            logger.error(s"[roboticsCallback] $msg")
            NotFound(msg)
        }
    }
