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

package uk.gov.hmrc.agentservicesaccount.models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

case class SubscriptionWorkItem(
  arn: Arn,
  subscriptionRequest: SubscriptionRequest,
  regime: LegacyRegime,
  agentReference: Option[String],
  sessionId: Option[String] = None // Only required for local testing against stubs. Always set to None for QA/Prod
)

object SubscriptionWorkItem:

  private def mongoReads(implicit crypto: Encrypter & Decrypter) = (__ \ "regime").read[LegacyRegime].flatMap { regime =>
    (
      (__ \ "arn").read[Arn] and
      (__ \ "subscriptionRequest").read[String](stringEncrypterDecrypter).map[SubscriptionRequest](string =>
        Json.parse(string).as[SubscriptionRequest](SubscriptionRequest.reads(regime))
      ) and
      Reads.pure(regime) and
      (__ \ "agentReference").readNullable[String] and
      (__ \ "sessionId").readNullable[String]
    )(SubscriptionWorkItem.apply)
  }
  private def mongoWrites(implicit crypto: Encrypter & Decrypter): Writes[SubscriptionWorkItem] =
    (
      (__ \ "arn").write[Arn] and
      (__ \ "subscriptionRequest").write[String](stringEncrypterDecrypter).contramap[SubscriptionRequest](subscriptionRequest =>
        Json.toJson(subscriptionRequest).toString
      ) and
      (__ \ "regime").write[LegacyRegime] and
      (__ \ "agentReference").writeNullable[String] and
      (__ \ "sessionId").writeNullable[String]
    )(o => Tuple.fromProductTyped(o))

  def mongoFormat(implicit crypto: Encrypter & Decrypter): Format[SubscriptionWorkItem] = Format(mongoReads, mongoWrites)
