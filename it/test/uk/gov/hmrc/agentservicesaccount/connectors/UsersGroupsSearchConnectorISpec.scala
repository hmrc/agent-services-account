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

package uk.gov.hmrc.agentservicesaccount.connectors

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.{get as wmGet}
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.scalatest.exceptions.TestFailedException
import uk.gov.hmrc.agentservicesaccount.models.CredId
import uk.gov.hmrc.agentservicesaccount.models.GroupId
import uk.gov.hmrc.agentservicesaccount.utils.ComponentSpecHelper
import uk.gov.hmrc.http.HeaderCarrier

class UsersGroupsSearchConnectorISpec
extends ComponentSpecHelper:

  private given HeaderCarrier = HeaderCarrier()

  private lazy val connector: UsersGroupsSearchConnector = app.injector.instanceOf[UsersGroupsSearchConnector]

  "getFirstAdminCredId" should {
    "return the first admin-equivalent user id for a group" in {
      stubGroupUsersResponse(
        """[
          |  {"userId":"user-1","credentialRole":"User"},
          |  {"userId":"admin-1","credentialRole":"Admin"},
          |  {"userId":"admin-2","credentialRole":"Admin"}
          |]""".stripMargin
      )

      connector.getFirstAdminCredId(GroupId("group-1")).futureValue shouldBe Some(CredId("user-1"))
    }

    "return an admin user id when users before it have no usable user id" in {
      stubGroupUsersResponse(
        """[
          |  {"credentialRole":"User"},
          |  {"userId":"admin-1","credentialRole":"Admin"}
          |]""".stripMargin
      )

      connector.getFirstAdminCredId(GroupId("group-1")).futureValue shouldBe Some(CredId("admin-1"))
    }

    "return none when the group has no admin-equivalent user id" in {
      stubGroupUsersResponse(
        """[
          |  {"userId":"assistant-1","credentialRole":"Assistant"},
          |  {"credentialRole":"Admin"}
          |]""".stripMargin
      )

      connector.getFirstAdminCredId(GroupId("group-1")).futureValue shouldBe None
    }

    "return none when the group is not found" in {
      stubFor(
        wmGet(urlEqualTo("/users-groups-search/groups/group-1/users"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getFirstAdminCredId(GroupId("group-1")).futureValue shouldBe None
    }

    "throw when users-groups-search returns an unexpected response" in {
      stubFor(
        wmGet(urlEqualTo("/users-groups-search/groups/group-1/users"))
          .willReturn(aResponse().withStatus(500).withBody("error"))
      )

      intercept[TestFailedException](connector.getFirstAdminCredId(GroupId("group-1")).futureValue)
    }
  }

  private def stubGroupUsersResponse(body: String): Unit =
    stubFor(
      wmGet(urlEqualTo("/users-groups-search/groups/group-1/users"))
        .willReturn(aResponse().withStatus(200).withBody(body))
    )
