///*
// * Copyright 2026 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.agentservicesaccount.controllers
//
//import play.api.http.Status.*
//import play.api.libs.json.*
//import play.api.libs.ws.{WSClient, WSResponse}
//import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
//import uk.gov.hmrc.agentservicesaccount.stubs.*
//import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
//import uk.gov.hmrc.domain.SaUtr
//
//import scala.util.{Failure, Success, Try}
//
//class AgentDetailsControllerHipISpec
//extends ComponentSpecHelper
//with AgentAuthStubs
//with AgentMappingStubs
//with DesStubs
//with HipStubs
//with InternalAuthStub
//with CitizenDetailsStubs
//with AgentAssuranceStubs
//with DmsSubmissionStubs
//with EmailStub {
//
//  override def beforeEach(): Unit = {
//    super.beforeEach()
//    wireMockServer.resetRequests()
//  }
//  override def extraConfig: Map[String, Any] = Map(
//    "auditing.enabled" -> false,
//    "stride.roles.agent-services-account" -> "maintain_agent_manually_assure",
//    "internal-auth-token-enabled-on-start" -> false,
//    "http-verbs.retries.intervals" -> List("1ms"),
//    "agent.entity.cache.enabled" -> false, // test are not ready for cache enabled
//    "agent.entity.cache.expires" -> "1 seconds",
//    "agent.entity-check.lock.expires" -> "1 seconds",
//    "agent.automap.lock.expires" -> "1 seconds",
//    "agent.entity-check.email.lock.expires" -> "1 seconds"
//  )
//
//  val testArn = Arn("AARN0000002")
//  val testArn2: Arn = Arn("AARN0000003")
//  val testUtr: Utr = Utr("7000000002")
//  val testSaUtr: SaUtr = SaUtr(testUtr.value)
//  val testUtr1: Utr = Utr("7000000003")
//  val testSaUtr1: SaUtr = SaUtr(testUtr1.value)
//
//  def clientUrl(arn: Arn) = s"/agent-record-with-checks/arn/${arn.value}"
//  val agentUrl = s"/agent-record-with-checks"
//  def postUrl(arn: Arn) = s"/agent/agency-details/arn/${arn.value}"
//
//  val wsClient: WSClient = app.injector.instanceOf[WSClient]
//
//  def expectedAgentRecordJson(
//    utr: Option[Utr],
//    suspensionStatus: Boolean,
//    isAnIndividual: Boolean
//  ): JsValue = {
//    val baseFields: Seq[(String, JsValue)] = Seq(
//      "agencyDetails" -> Json.obj(
//        "agencyName" -> "ABC Accountants",
//        "agencyEmail" -> "abc@xyz.com",
//        "agencyTelephone" -> "07345678901",
//        "agencyAddress" -> Json.obj(
//          "addressLine1" -> "Matheson House",
//          "addressLine2" -> "Grange Central",
//          "addressLine3" -> "Town Centre",
//          "addressLine4" -> "Telford",
//          "postalCode" -> "TF3 4ER",
//          "countryCode" -> "GB"
//        )
//      ),
//      "suspensionDetails" -> (
//        if (suspensionStatus)
//          Json.obj(
//            "suspensionStatus" -> suspensionStatus
//          )
//        else
//          Json.obj("suspensionStatus" -> suspensionStatus)
//      ),
//      "isAnIndividual" -> JsBoolean(isAnIndividual)
//    )
//
//    val utrField: Option[(String, JsValue)] = utr.map(u => "uniqueTaxReference" -> JsString(u.value))
//
//    val amlsField: Seq[(String, JsValue)] = Seq(
//      "amlsDetails" -> Json.obj(
//        "supervisoryBody" -> "HMRC",
//        "membershipNumber" -> "AMLS123",
//        "evidenceObjectReference" -> "evidence-ref-001"
//      )
//    )
//
//    JsObject(utrField.toSeq ++ baseFields ++ amlsField)
//  }
//
//  def retry[T](n: Int)(block: => T): T = {
//    Try(block) match {
//      case Success(result) => result
//      case Failure(e) if n > 1 =>
//        Thread.sleep(500)
//        retry(n - 1)(block)
//      case Failure(e) => throw e
//    }
//  }
//
//  "GET client /agent-services-account/agent-record-with-checks/arn/:arn" should {
//
//    "use HIP instead of DES and return suspension details" in {
//
//        stubInternalAuthorised()
//        givenHIPGetAgentRecordSuspendedAgent(testArn, testUtr.value)
//        givenCitizenIsAlive(testSaUtr)
//        givenAgentUtrCheckWithRefusalToDealWithFalse(testUtr)
//        givenAutoMappingCallSucceeds(testArn)
//
//        val response: WSResponse = get(clientUrl(testArn))
//
//        response.status shouldBe OK
//        response.json shouldBe expectedAgentRecordJson(
//          Some(testUtr),
//          suspensionStatus = true,
//          isAnIndividual = true
//        )
//
//        verifyDESWasNotCalled(testArn)
//      }
//
//  }
//
//}
