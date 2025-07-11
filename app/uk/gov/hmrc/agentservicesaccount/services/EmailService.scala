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

package uk.gov.hmrc.agentservicesaccount.services

import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.connectors.EmailConnector
import uk.gov.hmrc.agentservicesaccount.models.{EmailInformation, EntityCheckNotification}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class EmailService @Inject()(
  appConfig: AppConfig,
  emailConnector: EmailConnector
) {
  
  def sendEntityCheckNotification(
    entityCheckNotification: EntityCheckNotification
  )(using request: RequestHeader): Future[Unit] = {
    emailConnector.sendEmail(
      EmailInformation(
        to = Seq(appConfig.agentMaintainerEmail),
        templateId = "entity_check_notification",
        parameters = Map(
          "agencyName" -> entityCheckNotification.agencyName,
          "arn" -> entityCheckNotification.arn.value,
          "utr" -> entityCheckNotification.utr,
          "failedChecks" -> entityCheckNotification.failedChecks,
          "dateTime" -> entityCheckNotification.dateTime
        )
      )
    )
  }

}
