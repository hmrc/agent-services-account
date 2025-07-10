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

import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.http.Status.*
import play.api.libs.json.*
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.{BodyWritable, WSClient, WSResponse}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentservicesaccount.models.EmailInformation
import uk.gov.hmrc.agentservicesaccount.stubs.*
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.domain.SaUtr

import java.time.format.DateTimeFormatter
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}


class AgentDetailsControllerISpec
  extends ComponentSpecHelper
    with AgentAuthStubs
    with DesStubs
    with InternalAuthStub
    with CitizenDetailsStubs
    with AgentAssuranceStubs
    with DmsSubmissionStubs
    with EmailStub {


  override def extraConfig: Map[String, Any] = Map (
      "microservice.services.auth.host" -> mockHost,
      "microservice.services.auth.port" -> mockPort,
      "microservice.services.des.host" -> mockHost,
      "microservice.services.des.port" -> mockPort,
      "microservice.services.agent-assurance.host" -> mockHost,
      "microservice.services.agent-assurance.port" -> mockPort,
      "microservice.services.citizen-details.host" -> mockHost,
      "microservice.services.citizen-details.port" -> mockPort,
      "microservice.services.internal-auth.port" -> mockPort,
      "microservice.services.internal-auth.host" -> mockHost,
      "microservice.services.email.port" -> mockPort,
      "microservice.services.email.host" -> mockHost,
      "microservice.services.dms-submission.host" -> mockHost,
      "microservice.services.dms-submission.port" -> mockPort,
      "auditing.enabled" -> false,
      "stride.roles.agent-services-account" -> "maintain_agent_manually_assure",
      "internal-auth-token-enabled-on-start" -> false,
      "http-verbs.retries.intervals" -> List("1ms"),
      "agent.entity.cache.enabled" -> false, //test are not ready for cache enabled
      "agent.entity.cache.expires" -> "1 seconds",
      "agent.entity-check.lock.expires" -> "1 seconds",
      "agent.entity-check.email.lock.expires" -> "1 seconds"
  )

  val testArn = Arn("AARN0000002")
  val testArn2: Arn = Arn("AARN0000003")
  val testUtr: Utr = Utr("7000000002")
  val testSaUtr: SaUtr = SaUtr(testUtr.value)
  val testUtr1: Utr = Utr("7000000003")
  val testSaUtr1: SaUtr = SaUtr(testUtr1.value)


  def clientUrl(arn: Arn) = s"http://localhost:$port/agent-services-account/agent-record-with-checks/arn/${arn.value}"
  val agentUrl = s"http://localhost:$port/agent-services-account/agent-record-with-checks"
  def postUrl(arn:Arn) = s"http://localhost:$port/agent-services-account/agent/agency-details/arn/${arn.value}"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def doClientGetRequest(arn: Arn): WSResponse = Await.result(
    wsClient
      .url(clientUrl(arn))
      .withHttpHeaders("Authorization" -> "internal auth token")
      .get(),
    15.seconds
  )

  def doAgentGetRequest() = Await.result(
    wsClient
      .url(agentUrl)
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .get(),
    15.seconds
  )

  def doPOSTRequest[T](arn: Arn, body: T)(implicit wr: BodyWritable[T]) = Await.result(
    wsClient
      .url(postUrl(arn))
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .post(body),
    15.seconds
  )

  def expectedAgentRecordJson(utr: Option[Utr], suspensionStatus: Boolean, isAnIndividual: Boolean): JsValue = {
    val baseFields: Seq[(String, JsValue)] = Seq(
      "agencyDetails" -> Json.obj(
        "agencyName" -> "ABC Accountants",
        "agencyEmail" -> "abc@xyz.com",
        "agencyTelephone" -> "07345678901",
        "agencyAddress" -> Json.obj(
          "addressLine1" -> "Matheson House",
          "addressLine2" -> "Grange Central",
          "addressLine3" -> "Town Centre",
          "addressLine4" -> "Telford",
          "postalCode" -> "TF3 4ER",
          "countryCode" -> "GB"
        )
      ),
      "suspensionDetails" -> (
        if (suspensionStatus)
          Json.obj(
            "suspensionStatus" -> suspensionStatus,
            "regimes" -> Json.arr("ITSA")
          )
        else
          Json.obj("suspensionStatus" -> suspensionStatus)
        ),
      "isAnIndividual" -> JsBoolean(isAnIndividual)
    )

    val utrField: Option[(String, JsValue)] = utr.map(u => "uniqueTaxReference" -> JsString(u.value))

    JsObject(utrField.toSeq ++ baseFields)
  }


  private val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy h:mma")
  private val dateTime = formatter.format(localDateTime)

  private  def emailInformation(arn: Arn, utr:Utr, failedChecks: List[String]) =     EmailInformation(
    to = Seq("test@example.com"),
    templateId = "entity_check_notification",
    parameters = Map(
      "arn" -> arn.value,
      "dateTime" -> dateTime,
      "agencyName" -> "ABC Accountants",
      "failedChecks" -> failedChecks.mkString("|"),
      "utr" -> utr.value
    ),
    force = true
  )

  def retry[T](n: Int)(block: => T): T = {
    Try(block) match {
      case Success(result) => result
      case Failure(e) if n > 1 =>
        Thread.sleep(500)
        retry(n - 1)(block)
      case Failure(e) => throw e
    }
  }

  "GET client /agent-services-account/agent-record-with-checks/arn/:arn" should {

    "return suspension details when agent record contains suspension details" in {
      stubInternalAuthorised()
      givenDESGetAgentRecordSuspendedAgent(testArn, Some(testUtr))
      givenCitizenIsAlive(testSaUtr)
      givenAgentUtrCheckWithRefusalToDealWithFalse(testUtr)

      val response: WSResponse  = doClientGetRequest(testArn)

      response.json shouldBe expectedAgentRecordJson(Some(testUtr), suspensionStatus = true, isAnIndividual = true)
      response.status shouldBe OK

    }

    "return suspension details and send email for deceased" in {
      retry(5) {
        stubInternalAuthorised()
        givenDESGetAgentRecordSuspendedAgent(testArn, Some(testUtr))
        givenCitizenIsDeceased(testSaUtr)
        givenAgentUtrCheckWithRefusalToDealWithTrue(testUtr)
        givenEmailSent(emailInformation(testArn, testUtr, List("Agent is deceased", "Agent is on the 'Refuse To Deal With' list")))

        val response: WSResponse = doClientGetRequest(testArn)

        response.json shouldBe expectedAgentRecordJson(Some(testUtr), suspensionStatus = true, isAnIndividual = true)
        response.status shouldBe OK
        verifyEmailRequestWasSent(1)
      }

    }

    "return OK when DES returns agent record with no UTR" in {
      stubInternalAuthorised()
      givenDESGetAgentRecordSuspendedAgent(testArn, None)

      val response = doClientGetRequest(testArn)

      response.status shouldBe OK
      response.json shouldBe expectedAgentRecordJson(None, suspensionStatus = true, isAnIndividual = true)

  }

    "return 401 when internal auth is not provided for clientVerifyEntity" in {
      val response = Await.result(
        wsClient
          .url(clientUrl(testArn))
          .get(), // no auth header
        10.seconds
      )

      response.status shouldBe UNAUTHORIZED
    }

    "return 404 when DES fails" in {
      stubInternalAuthorised()
      givenAgentIsUnknown404(testArn)

      val response = doClientGetRequest(testArn)

      response.status shouldBe NOT_FOUND
    }
  }

  "GET agent /agent-services-account/agent-record-with-checks" should {
    "return agentRecord and DO NOT send out email when isRefusalToDealWith is false" in {
      isLoggedInAsASAgent(testArn2)
      givenDESGetAgentRecord(testArn2, Some(testUtr1))
      givenCitizenIsAlive(testSaUtr1)
      givenAgentUtrCheckWithRefusalToDealWithFalse(testUtr1)

      val response = doAgentGetRequest()

      response.status shouldBe OK
      response.json shouldBe expectedAgentRecordJson(Some(testUtr1), suspensionStatus = false, isAnIndividual = false)

      verifyEmailRequestWasSent(0)
    }


    "after lock expire return agent record and and send out email if agent is on refusalToDealWith" in {
      retry(5) {
        isLoggedInAsASAgent(testArn2)
        givenDESGetAgentRecord(testArn2, Some(testUtr1))
        givenCitizenIsAlive(testSaUtr1)
        givenAgentUtrCheckWithRefusalToDealWithTrue(testUtr1)
        givenEmailSent(emailInformation(testArn2, testUtr1, List("Agent is on the 'Refuse To Deal With' list")))

        val response = doAgentGetRequest()

        response.status shouldBe OK
        response.json shouldBe expectedAgentRecordJson(Some(testUtr1), suspensionStatus = false, isAnIndividual = false)

        verifyEmailRequestWasSent(1)
      }
    }
  }

  "POST /agent/agency-details/:arn" should {
    "return ACCEPTED status when the DMS submission was successful" in {
      isLoggedInAsStride("stride")
      givenDmsSubmissionSuccess

      val html = "<html><head></head><body></body></html>"
      val encodedHtmlStr = java.util.Base64.getEncoder.encodeToString(html.getBytes())
      val response = doPOSTRequest(testArn, encodedHtmlStr)
      response.status shouldBe CREATED
    }

    "return internal server error when payload is not encoded" in {
      isLoggedInAsStride("stride")

      val response = doPOSTRequest(testArn, s"""{"a":"b"}""")
      response.status shouldBe INTERNAL_SERVER_ERROR
      response.body.contains("build PDF failed with error:")
    }

    "return internal server error when payload empty" in {
      isLoggedInAsStride("stride")

      val response = doPOSTRequest(testArn, "")
      response.status shouldBe INTERNAL_SERVER_ERROR
      response.body.contains("base64 encoding failed with field not provided")
    }
  }

}
