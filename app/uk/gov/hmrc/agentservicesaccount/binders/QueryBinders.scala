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

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.agentservicesaccount.models.subscription.AgentRegime

object QueryBinders {

  implicit def agentRegimeBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[AgentRegime] =
    new QueryStringBindable[AgentRegime] {
      override def bind(
        key: String,
        params: Map[String, Seq[String]]
      ): Option[Either[String, AgentRegime]] = stringBinder.bind(key, params).map {
        case Right(value) =>
          AgentRegime.values
            .find(_.toString == value)
            .map(Right(_))
            .getOrElse(Left(s"Invalid legacy regime: $value"))
        case Left(error) => Left(error)
      }

      override def unbind(
                           key: String,
                           agentRegime: AgentRegime
      ): String = stringBinder.unbind(key, agentRegime.toString)
    }
}
