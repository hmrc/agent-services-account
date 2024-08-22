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

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext

import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import uk.gov.hmrc.agentservicesaccount.models.ChangeOfDetailsRequest
import uk.gov.hmrc.agentservicesaccount.services.ChangeOfDetailsRequestService
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton()
class ChangeOfDetailsRequestController @Inject() (
    changeOfDetailsRequestService: ChangeOfDetailsRequestService,
    cc: ControllerComponents
)(using ec: ExecutionContext)
    extends BackendController(cc):

  def find(arn: String): Action[AnyContent] = Action.async: request =>
    given Request[AnyContent] = request

    changeOfDetailsRequestService
      .find(arn)
      .map:
        case Some(changeOfDetailsRequest) =>
          Ok(Json.toJson(changeOfDetailsRequest)(ChangeOfDetailsRequest.format))
        case None =>
          NotFound

  def upsert(): Action[ChangeOfDetailsRequest] =
    Action.async(parse.json[ChangeOfDetailsRequest](ChangeOfDetailsRequest.format)): request =>
      given Request[ChangeOfDetailsRequest] = request

      changeOfDetailsRequestService
        .upsert(request.body)
        .map(_ => NoContent)
        .recover:
          case _ =>
            throw new InternalServerException("Failed to upsert ChangeOfDetailsRequest for ARN: " + request.body.arn)

  def delete(arn: String): Action[AnyContent] = Action.async: request =>
    given Request[AnyContent] = request

    changeOfDetailsRequestService
      .delete(arn)
      .map:
        case result if result.wasAcknowledged() && result.getDeletedCount >= 1 =>
          NoContent
        case _ =>
          NotFound
