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

import cats.data.EitherT
import play.api.libs.json.*
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsJson
import play.api.mvc.Result
import play.api.mvc.Results.BadRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object RequestValidation:

  type ResultT[A] = EitherT[Future, Result, A]

  def invalidPayload(message: String): Result =
    BadRequest(Json.obj("code" -> "INVALID_PAYLOAD", "message" -> message))

  extension (content: AnyContent)
    def toJsonEitherT(using ExecutionContext): ResultT[JsValue] =
      content match
        case AnyContentAsJson(json) => EitherT.rightT(json)
        case _ => EitherT.leftT(invalidPayload("Expected JSON body"))

  extension (json: JsValue)
    def validateEitherT[A](using Reads[A], ExecutionContext): ResultT[A] =
      json.validate[A] match
        case JsSuccess(value, _) => EitherT.rightT(value)
        case JsError(errors) =>
          val message = errors.flatMap(_._2).map(_.message).mkString(", ")
          EitherT.leftT(invalidPayload(s"Invalid JSON: $message"))

  extension [A](e: Either[String, A])
    def toResultEitherT(using ExecutionContext): ResultT[A] =
      e match
        case Right(value) => EitherT.rightT(value)
        case Left(err) => EitherT.leftT(invalidPayload(err))
