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

package uk.gov.hmrc.agentservicesaccount.auth

import play.api.Logging
import play.api.mvc.*
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.controllers.ErrorResults.NoPermission
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, allEnrolments, credentials, groupIdentifier}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import javax.inject.{Inject, Singleton}

@Singleton
class AuthActions @Inject() (val authConnector: AuthConnector, cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with AuthorisedFunctions
    with Logging {

  private def getEnrolmentInfo(
    enrolment: Set[Enrolment],
    enrolmentKey: String,
    identifier: String
  ): Option[String] = enrolment.find(_.key.equals(enrolmentKey)).flatMap(_.identifiers.find(_.key.equals(identifier)).map(_.value))

  def hasRequiredStrideRole(
    enrolments: Enrolments,
    strideRoles: Seq[String]
  ): Boolean = strideRoles.exists(s => enrolments.enrolments.exists(_.key == s))

  private type AuthorisedRequestWithArn = Request[AnyContent] => Arn => Future[Result]
  private type AuthorisedRequestWithArnAndCredId = Request[AnyContent] => Arn => CredId => GroupId => Future[Result]

  def authorisedWithArn(body: AuthorisedRequestWithArn): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(allEnrolments) { enrol =>
        getEnrolmentInfo(
          enrol.enrolments,
          "HMRC-AS-AGENT",
          "AgentReferenceNumber"
        ) match {
          case Some(arn) => body(request)(Arn(arn))
          case _ => Future.successful(NoPermission)
        }
      }
      .recoverWith {
        case ex: NoActiveSession =>
          logger.warn("NoActiveSession", ex)
          Future.successful(Unauthorized)
      }
  }

  def authorisedWithArnAndGroupId[A](body: Request[AnyContent] => (Arn, GroupId) => Future[Result]): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(allEnrolments and groupIdentifier) {
        case enrol ~ Some(groupId) =>
          getEnrolmentInfo(
            enrol.enrolments,
            "HMRC-AS-AGENT",
            "AgentReferenceNumber"
          ) match {
            case Some(arn) => body(request)(Arn(arn), GroupId(groupId.toString))
            case _ => Future.successful(NoPermission)
          }
      }
      .recoverWith {
        case ex: NoActiveSession =>
          logger.warn("NoActiveSession", ex)
          Future.successful(Unauthorized)
      }
  }

  def authorisedWithArnAndCredId(body: AuthorisedRequestWithArnAndCredId): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(allEnrolments.and(credentials).and(groupIdentifier)) {
        case enrol ~ optCreds ~ optGroupId =>
          val arnOpt = getEnrolmentInfo(
            enrol.enrolments,
            "HMRC-AS-AGENT",
            "AgentReferenceNumber"
          )
          val credIdOpt = optCreds.map(creds => CredId(creds.providerId))
          val groupIdOpt = optGroupId.map(id => GroupId(id.toString))

          (arnOpt, credIdOpt, groupIdOpt) match
            case (Some(arn), Some(credId), Some(groupId)) => body(request)(Arn(arn))(credId)(groupId)
            case _ => Future.successful(NoPermission)
      }
      .recoverWith {
        case ex: NoActiveSession =>
          logger.warn("NoActiveSession", ex)
          Future.successful(Unauthorized)
      }
  }

  def withAffinityGroupAgentOrStride(strideRoles: Seq[String])(
    action: Request[AnyContent] => Future[Result]
  ): Action[AnyContent] = Action.async { implicit request =>
    authorised().retrieve(allEnrolments.and(affinityGroup).and(credentials)) {
      case enrolments ~ affinityGroup ~ optCreds =>
        optCreds
          .collect {
            case creds @ Credentials(_, "GovernmentGateway") if affinityGroup.contains(AffinityGroup.Agent) => creds
            case creds @ Credentials(_, "PrivilegedApplication") if hasRequiredStrideRole(enrolments, strideRoles) => creds
          }
          .map(_ => action(request))
          .getOrElse(Future.successful(Forbidden))
    }
  }

}
