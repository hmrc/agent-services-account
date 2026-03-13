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

package uk.gov.hmrc.agentservicesaccount.models.subscription

import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.Json
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec

class RoboticsInvocationRequestSpec
extends UnitSpec:

  "RoboticsInvocationRequest" should:
    "wrap operation data in a single string argument" in:
      val operationDataJsonString = Json.stringify(Json.obj("requestId" -> "request-123", "targetSystem" -> "CESA"))

      val result = RoboticsInvocationRequest.fromOperationData(operationDataJsonString)

      result shouldBe RoboticsInvocationRequest(
        requestData = Seq(
          RoboticsInvocationRequestData(
            workflowData = RoboticsWorkflowData(
              arguments = Seq(
                RoboticsArgument(
                  `type` = "string",
                  value = operationDataJsonString
                )
              )
            )
          )
        )
      )

    "serialize argument value as a JSON string (not nested object)" in:
      val operationDataJsonString = Json.stringify(Json.obj("requestId" -> "request-456", "operationRequired" -> "CREATE"))
      val payload = Json.toJson(RoboticsInvocationRequest.fromOperationData(operationDataJsonString))

      payload shouldBe Json.obj(
        "requestData" -> Json.arr(
          Json.obj(
            "workflowData" -> Json.obj(
              "arguments" -> Json.arr(
                Json.obj(
                  "type" -> "string",
                  "value" -> operationDataJsonString
                )
              )
            )
          )
        )
      )

      val requestData = (payload \ "requestData").as[JsArray]
      val arguments = ((requestData.value.head \ "workflowData") \ "arguments").as[JsArray]
      (arguments.value.head \ "value").get shouldBe JsString(operationDataJsonString)
