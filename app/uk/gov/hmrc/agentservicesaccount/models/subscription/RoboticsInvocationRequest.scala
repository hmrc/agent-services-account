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

import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes

final case class RoboticsInvocationRequest(
  requestData: Seq[RoboticsInvocationRequestData]
)

final case class RoboticsInvocationRequestData(
  workflowData: RoboticsWorkflowData
)

final case class RoboticsWorkflowData(
  arguments: Seq[RoboticsArgument]
)

final case class RoboticsArgument(
  `type`: String,
  value: String
)

object RoboticsInvocationRequest:

  // Contract note: HIP robotics invocation expects operation data in `workflowData.arguments[*].value` as a JSON
  // string, not a nested JSON object.
  // Reference: https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=1194459607
  def fromOperationData(operationDataJsonString: String): RoboticsInvocationRequest =
    RoboticsInvocationRequest(
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

  given Writes[RoboticsArgument] = Json.writes[RoboticsArgument]
  given Writes[RoboticsWorkflowData] = Json.writes[RoboticsWorkflowData]
  given Writes[RoboticsInvocationRequestData] = Json.writes[RoboticsInvocationRequestData]
  given OWrites[RoboticsInvocationRequest] = Json.writes[RoboticsInvocationRequest]
