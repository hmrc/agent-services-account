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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.{CredId, GroupId}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import java.util.UUID

case class SubscriptionWorkItem(
  arn: Arn,
  subscriptionRequest: SubscriptionRequest,
  regime: LegacyRegime,
  agentReference: Option[AgentReference],
  groupId: GroupId,
  adminCredId: CredId,
  requestId: String = UUID.randomUUID().toString,
  roboticsInvokedAt: Option[Instant] = None,
  sessionId: Option[String] = None, // Local stub-only: ESP stubs require X-Session-ID; keep None for QA/Prod.
  bearerToken: Option[String] = None // Local stub-only: ESP stubs require Authorization; never persist in QA/Prod.
)

object SubscriptionWorkItem:

  private def mongoReads(using crypto: Encrypter & Decrypter) = (__ \ "regime").read[LegacyRegime].flatMap { regime =>
    (
      (__ \ "arn").read[Arn] and
        (__ \ "subscriptionRequest").read[String](stringEncrypterDecrypter).map[SubscriptionRequest](string =>
          Json.parse(string).as[SubscriptionRequest](SubscriptionRequest.reads(regime))
        ) and
        Reads.pure(regime) and
        (__ \ "agentReference").readNullable[AgentReference] and
        (__ \ "groupId").read[GroupId] and
        (__ \ "adminCredId").read[CredId] and
        (__ \ "requestId").read[String] and
        (__ \ "roboticsInvokedAt").readNullable[Instant](MongoJavatimeFormats.instantFormat) and
        (__ \ "sessionId").readNullable[String] and
        (__ \ "bearerToken").readNullable[String]
    )(SubscriptionWorkItem.apply)
  }

  private def mongoWrites(using crypto: Encrypter & Decrypter): Writes[SubscriptionWorkItem] =
    (
      (__ \ "arn").write[Arn] and
        (__ \ "subscriptionRequest").write[String](stringEncrypterDecrypter).contramap[SubscriptionRequest](subscriptionRequest =>
          Json.toJson(subscriptionRequest).toString
        ) and
        (__ \ "regime").write[LegacyRegime] and
        (__ \ "agentReference").writeNullable[AgentReference] and
        (__ \ "groupId").write[GroupId] and
        (__ \ "adminCredId").write[CredId] and
        (__ \ "requestId").write[String] and
        (__ \ "roboticsInvokedAt").writeNullable[Instant](MongoJavatimeFormats.instantFormat) and
        (__ \ "sessionId").writeNullable[String] and
        (__ \ "bearerToken").writeNullable[String]
    )(o => Tuple.fromProductTyped(o))

  def mongoFormat(using crypto: Encrypter & Decrypter): Format[SubscriptionWorkItem] = Format(mongoReads, mongoWrites)
