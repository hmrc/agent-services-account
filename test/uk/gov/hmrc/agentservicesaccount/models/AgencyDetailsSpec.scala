/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.matchers.must.Matchers.*
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.agentservicesaccount.utils.UnitSpec
import uk.gov.hmrc.crypto.Crypted
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.crypto.PlainBytes
import uk.gov.hmrc.crypto.PlainContent
import uk.gov.hmrc.crypto.PlainText

class AgencyDetailsSpec
extends UnitSpec:

  given fakeCrypto: Encrypter
    with Decrypter
    with

    override def encrypt(plain: PlainContent): Crypted =
      plain match {
        case PlainText(value) => Crypted(s"ENC(${value})")
        case PlainBytes(value) => Crypted(s"ENC(${value})")
      }

    override def decrypt(crypted: Crypted): PlainText =
      val raw = crypted.value.stripPrefix("ENC(").stripSuffix(")")
      PlainText(raw)

    override def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes =
      val raw = reversiblyEncrypted.value.stripPrefix("ENC(").stripSuffix(")")
      PlainBytes(raw.getBytes("UTF-8"))

  "AgencyDetails" should {
    "serialize and deserialize using encrypted format" in {
      val agencyDetails = AgencyDetails(
        Some("Agency Name"),
        Some("email@example.com"),
        Some("123456789"),
        Some(BusinessAddress(
          "line1",
          None,
          None,
          None,
          Some("AB1 2CD"),
          "GB"
        ))
      )

      val json = Json.toJson(agencyDetails)(using AgencyDetails.agencyDetailsDatabaseFormat)
      val result = Json.fromJson[AgencyDetails](json)(using AgencyDetails.agencyDetailsDatabaseFormat).get

      result mustBe agencyDetails
    }
  }

  "BusinessAddress" should {
    "roundtrip through encryption format" in {
      val addr = BusinessAddress(
        "line1",
        Some("line2"),
        None,
        None,
        Some("ZZ1 1ZZ"),
        "GB"
      )
      val json = Json.toJson(addr)(using BusinessAddress.businessAddressDatabaseFormat)
      val result = Json.fromJson[BusinessAddress](json)(using BusinessAddress.businessAddressDatabaseFormat).get

      result mustBe addr
    }
  }
