/*
 * Copyright 2023 HM Revenue & Customs
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

package models.responses

import domain.{AccountWithAuthorities, StandingAuthority}
import models.{AccountNumber, AccountStatus, AccountType, EORI}
import play.api.libs.json.{JsSuccess, Json}
import utils.SpecBase
import utils.TestData.{BANK_ACCOUNT, DATE_STRING, EORI_VALUE_1}

class StandingAuthoritiesResponseSpec extends SpecBase {

  "standingAuthoritiesResponseFormat" should {

    "generate correct output for Json Reads" in new Setup {
      import StandingAuthoritiesResponse.standingAuthoritiesResponseFormat

      Json.fromJson(Json.parse(standingAuthoritiesResponseJsonString)) mustBe JsSuccess(
        standingAuthoritiesResponseObject
      )
    }

    "generate correct output for Json Writes" in new Setup {
      Json.toJson(standingAuthoritiesResponseObject) mustBe Json.parse(standingAuthoritiesResponseJsonString)
    }
  }

  trait Setup {

    val standingAuthoritiesResponseJsonString: String =
      """{
        |"ownerEori":"someEORI",
        |"accounts":[{
        |"accountType":"CDSCash",
        |"accountNumber":"1234567890987",
        |"accountStatus":"Open",
        |"ownerEori":"GB12345676",
        |"authorities":[{
        |"authorisedEori":"someEORI",
        |"authorisedFromDate":"2024-05-28",
        |"viewBalance":true,
        |"authorisedToDate":"2024-05-28"
        |}]
        |}]
        |}""".stripMargin

    val accountAuthority: StandingAuthority =
      StandingAuthority(EORI(EORI_VALUE_1), DATE_STRING, Some(DATE_STRING), true)

    val accountWithAuthorities: Seq[AccountWithAuthorities] =
      Seq(
        AccountWithAuthorities(
          AccountType("CDSCash"),
          AccountNumber(BANK_ACCOUNT),
          AccountStatus("Open"),
          Some(EORI("GB12345676")),
          Seq(accountAuthority)
        )
      )

    val standingAuthoritiesResponseObject: StandingAuthoritiesResponse =
      StandingAuthoritiesResponse(EORI(EORI_VALUE_1), accountWithAuthorities)
  }
}
