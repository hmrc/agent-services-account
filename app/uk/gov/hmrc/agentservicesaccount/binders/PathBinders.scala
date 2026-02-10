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

package uk.gov.hmrc.agentservicesaccount.binders

import play.api.mvc.PathBindable
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime

object PathBinders {

  implicit val arnBinder: PathBindable[Arn] =
    new PathBindable[Arn] {
      override def bind(
        key: String,
        value: String
      ): Either[String, Arn] = {
        if (Arn.isValid(value)) {
          Right(Arn(value))
        }
        else {
          Left("Invalid ARN")
        }
      }

      override def unbind(
        key: String,
        arn: Arn
      ): String = arn.value
    }

  implicit val legacyRegimeBinder: PathBindable[LegacyRegime] =
    new PathBindable[LegacyRegime] {
      override def bind(
        key: String,
        value: String
      ): Either[String, LegacyRegime] = LegacyRegime.values
        .find(_.toString == value)
        .map(Right(_))
        .getOrElse(Left(s"Invalid legacy regime: $value"))

      override def unbind(
        key: String,
        legacyRegime: LegacyRegime
      ): String = legacyRegime.toString
    }

}
