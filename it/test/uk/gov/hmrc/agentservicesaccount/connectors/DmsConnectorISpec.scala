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

package uk.gov.hmrc.agentservicesaccount.connectors

import com.typesafe.config.Config
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import play.api.mvc.{AnyContentAsEmpty, MultipartFormData, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentservicesaccount.config.AppConfig
import uk.gov.hmrc.agentservicesaccount.stubs.{DataStreamStub, DesStubs, DmsSubmissionStubs}
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.http.client.HttpClientV2

import java.time.{LocalDateTime, ZoneId}
import scala.concurrent.ExecutionContext

class DmsConnectorISpec 
  extends ComponentSpecHelper 
    with DesStubs
    with DataStreamStub
    with DmsSubmissionStubs {
  
  implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  implicit val configuration: Config = app.injector.instanceOf[Config]
  private implicit lazy val as: ActorSystem = ActorSystem()
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val request: Request[AnyContentAsEmpty.type] = FakeRequest()

  val dmsConnector =
    new DmsConnector(
      app.injector.instanceOf[HttpClientV2],
      appConfig,
      configuration,
      as
    )

  override def extraConfig: Map[String, Any] = Map(
      "internal-auth-token-enabled-on-start" -> false,
      "microservice.services.internal-auth.host" -> mockHost,
      "microservice.services.internal-auth.port" -> mockPort,
      "microservice.services.dms-submission.port" -> mockPort,
      "microservice.services.dms-submission.host" -> mockHost,
      "microservice.services.dms-submission.contact-details-submission.callbackEndpoint" -> "http://localhost/callback",
      "microservice.services.dms-submission.contact-details-submission.classificationType" -> "classificationType",
      "microservice.services.dms-submission.contact-details-submission.source" -> "source",
      "microservice.services.dms-submission.contact-details-submission.formId" -> "formId",
      "microservice.services.dms-submission.contact-details-submission.customerId" -> "customerId",
      "microservice.services.dms-submission.contact-details-submission.businessArea" -> "businessArea",
      "internal-auth.token" -> "authKey",
      "http-verbs.retries.intervals" -> List("1ms"),
      "auditing.enabled" -> false
    )

  "DmsConnector sendPdf" should {

//    val submissionReference = "submissionReference"

    val timestamp =
      LocalDateTime
        .of(
          2022,
          3,
          2,
          12,
          30,
          45
        )
        .atZone(ZoneId.of("UTC"))
        .toInstant

    val sourcePart: Source[ByteString, NotUsed] = Source.single(ByteString.fromString("SomePdfBytes"))
    val source: Source[
      MultipartFormData.Part[Source[ByteString, NotUsed]]
        & Serializable,
      NotUsed
    ] = Source(
      Seq(
        DataPart("callbackUrl", "http://localhost/callback"),
        MultipartFormData.DataPart("submissionReference", "submissionReference"),
        DataPart("metadata.source", "source"),
        DataPart("metadata.timeOfReceipt", timestamp.toString),
        DataPart("metadata.formId", "formId"),
        DataPart("metadata.customerId", "customerId"),
        DataPart("metadata.classificationType", "classificationType"),
        DataPart("metadata.businessArea", "businessArea"),
        FilePart(
          key = "form",
          filename = "form.pdf",
          contentType = Some("application/pdf"),
          ref = sourcePart
        )
      )
    )

    "must return Done when the server returns ACCEPTED" in {
      givenDmsFullSubmissionSuccess
      dmsConnector.sendPdf(source).futureValue
    }

    "must fail when the server returns another status" in {
      givenDmsSubmission5xx
      dmsConnector.sendPdf(source).failed.futureValue
    }
  }

}
