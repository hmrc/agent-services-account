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

package uk.gov.hmrc.agentservicesaccount.assets

import uk.gov.hmrc.agentservicesaccount.models.ChangeOfDetailsRequest

import java.time.Instant
import java.time.temporal.ChronoUnit

object TestConstants:
  
  val testTimeSubmitted: Instant = Instant.now.truncatedTo(ChronoUnit.SECONDS)

  val testChangeOfDetailsRequest: ChangeOfDetailsRequest = ChangeOfDetailsRequest("AARN1234567", testTimeSubmitted)
