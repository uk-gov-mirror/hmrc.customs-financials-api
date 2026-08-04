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

package controllers

import config.MetaConfig.Platform.{ENROLMENT_IDENTIFIER, ENROLMENT_KEY}
import domain.*
import models.requests.manageAuthorities.*
import models.{AccountNumber, AccountStatus, AccountType, EORI}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.*
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsJson, Result}
import play.api.test.*
import play.api.test.Helpers.*
import play.api.{Application, inject}
import services.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.TestData.EORI_VALUE
import utils.SpecBase

import scala.concurrent.{ExecutionContext, Future}

class AccountAuthoritiesControllerSpec extends SpecBase {

  "AccountAuthoritiesController.get" should {

    "delegate to the service and return a list of account authorities with a 200 status code" in new Setup {
      val accountWithAuthorities: Seq[AccountWithAuthorities] = Seq(
        AccountWithAuthorities(
          AccountType("CDSCash"),
          AccountNumber("12345"),
          AccountStatus("Open"),
          Some(EORI("GB12345676")),
          Seq.empty
        )
      )

      when(mockAccountAuthorityService.getAccountAuthorities(eqTo(traderEORI)))
        .thenReturn(Future.successful(accountWithAuthorities))

      running(app) {
        val result = route(app, requestWithEori).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(accountWithAuthorities)
      }
    }

    "return an error when no EORINumber found in auth record" in new Setup {
      when(mockAuthConnector.authorise[Enrolments](any, any)(any, any))
        .thenReturn(Future.successful(Enrolments(Set())))

      running(app) {
        val result = route(app, getRequestNoEori).value

        status(result) mustBe FORBIDDEN
        contentAsString(result) mustBe "Enrolment Identifier EORINumber not found"
      }
    }

    "return 503 (service unavailable)" when {
      "get account authorities call fails with BadRequestException (4xx) " in new Setup {
        when(mockAccountAuthorityService.getAccountAuthorities(eqTo(traderEORI)))
          .thenReturn(Future.failed(UpstreamErrorResponse("4xx", FORBIDDEN, FORBIDDEN)))

        running(app) {
          val result = route(app, requestWithEori).value
          status(result) mustBe SERVICE_UNAVAILABLE
        }
      }
    }

    "get account authorities call fails with InternalServerException (5xx) " in new Setup {
      when(mockAccountAuthorityService.getAccountAuthorities(eqTo(traderEORI)))
        .thenReturn(Future.failed(UpstreamErrorResponse("5xx", SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)))

      running(app) {
        val result = route(app, requestWithEori).value
        status(result) mustBe SERVICE_UNAVAILABLE
      }
    }

    "return 500 (InternalServerError)" when {
      "get account authorities call fails with InternalServerException (5xx) and error message contains" +
        " 'JSON validation'" in new Setup {
          when(mockAccountAuthorityService.getAccountAuthorities(eqTo(traderEORI)))
            .thenReturn(Future.failed(UpstreamErrorResponse("JSON validation", INTERNAL_SERVER_ERROR)))

          running(app) {
            val result = route(app, requestWithEori).value

            status(result) mustBe INTERNAL_SERVER_ERROR
          }
        }
    }

    "return 200 (OK) and return empty AccountWithAuthorities" when {

      "get account authorities call fails with BAD_REQUEST and contains " +
        "could not find accounts related to eori message in SourceFaultDetail" in new Setup {

          when(mockAccountAuthorityService.getAccountAuthorities(eqTo(traderEORI)))
            .thenReturn(Future.failed(UpstreamErrorResponse(noAccountsForEoriMsg, BAD_REQUEST)))

          running(app) {
            val result = route(app, requestWithEori).value

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.toJson(Seq.empty[AccountWithAuthorities])
          }
        }
    }
  }

  "AccountAuthoritiesController.grant" when {

    val grantAuthorityRequest = GrantAuthorityRequest(
      Accounts(Some("345"), Seq("123", "754"), Some("54345")),
      StandingAuthority(EORI("authorisedEori"), "2018-11-09", None, viewBalance = true),
      AuthorisedUser("some name", "some role"),
      editRequest = false,
      EORI(EORI_VALUE)
    )

    "request is valid and API call is successful" should {
      "delegate to the service and return a 204 status code" in new Setup {
        when(mockAccountAuthorityService.grantAccountAuthorities(eqTo(grantAuthorityRequest))(any))
          .thenReturn(Future.successful(true))

        running(app) {
          val result = route(app, grantRequest(grantAuthorityRequest)).value
          status(result) mustBe NO_CONTENT
        }
      }
    }

    "delegate to the service and auditEditAuthority" in new Setup {

      val editAuth: GrantAuthorityRequest = grantAuthorityRequest.copy(editRequest = true)

      when(mockAccountAuthorityService.grantAccountAuthorities(eqTo(editAuth))(any))
        .thenReturn(Future.successful(true))

      running(app) {
        val result = route(app, grantRequest(editAuth)).value
        status(result) mustBe NO_CONTENT
      }
    }

    "request is valid but API call fails" should {
      "return 500" in new Setup {
        when(mockAccountAuthorityService.grantAccountAuthorities(eqTo(grantAuthorityRequest))(any))
          .thenReturn(Future.successful(false))

        running(app) {
          val result = route(app, grantRequest(grantAuthorityRequest)).value
          status(result) mustBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "request JSON is invalid" should {
      "return 400" in new Setup {
        val invalidRequest: FakeRequest[AnyContentAsJson] =
          FakeRequest(POST, controllers.routes.AccountAuthoritiesController.grant().url)
            .withJsonBody(Json.parse("""{"valid": "nope"}"""))

        running(app) {
          val result = route(app, invalidRequest).value
          status(result) mustBe BAD_REQUEST
        }
      }
    }

    "no EORINumber found in auth record" should {
      "return 403" in new Setup {
        when(mockAuthConnector.authorise[Enrolments](any, any)(any, any))
          .thenReturn(Future.successful(Enrolments(Set())))

        running(app) {
          val result = route(app, getRequestNoEori).value

          status(result) mustBe FORBIDDEN
          contentAsString(result) mustBe "Enrolment Identifier EORINumber not found"
        }
      }
    }
  }

  "AccountAuthoritiesController.revoke" when {

    val revokeAuthorityRequest = RevokeAuthorityRequest(
      AccountNumber("123"),
      CdsCashAccount,
      EORI("authorisedEori"),
      AuthorisedUser("some name", "some role"),
      EORI("GB12345")
    )

    "request is valid and API call is successful" should {
      "delegate to the service and return a 204 status code" when {
        "revoking for a cash account" in new Setup {
          when(
            mockAccountAuthorityService.revokeAccountAuthorities(eqTo(revokeAuthorityRequest))(any)
          )
            .thenReturn(Future.successful(true))

          running(app) {
            val result = route(app, revokeRequest(revokeAuthorityRequest)).value
            status(result) mustBe NO_CONTENT
          }
        }
      }
    }

    "revoking for a guarantee account" in new Setup {
      val revokeGuaranteeRequest: RevokeAuthorityRequest =
        revokeAuthorityRequest.copy(accountType = CdsGeneralGuaranteeAccount)

      when(
        mockAccountAuthorityService.revokeAccountAuthorities(eqTo(revokeGuaranteeRequest))(any)
      )
        .thenReturn(Future.successful(true))

      running(app) {
        val result = route(app, revokeRequest(revokeGuaranteeRequest)).value
        status(result) mustBe NO_CONTENT
      }
    }

    "revoking for a deferment account" in new Setup {
      val revokeDefermentRequest: RevokeAuthorityRequest =
        revokeAuthorityRequest.copy(accountType = CdsDutyDefermentAccount)

      when(
        mockAccountAuthorityService.revokeAccountAuthorities(eqTo(revokeDefermentRequest))(any)
      )
        .thenReturn(Future.successful(true))

      running(app) {
        val result = route(app, revokeRequest(revokeDefermentRequest)).value
        status(result) mustBe NO_CONTENT
      }
    }

    "request is valid but API call fails" should {
      "return 500" in new Setup {
        when(mockAccountAuthorityService.revokeAccountAuthorities(eqTo(revokeAuthorityRequest))(any))
          .thenReturn(Future.successful(false))

        running(app) {
          val result = route(app, revokeRequest(revokeAuthorityRequest)).value
          status(result) mustBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "request JSON is invalid" should {
      "return 400" in new Setup {

        val invalidRequest: FakeRequest[AnyContentAsJson] =
          FakeRequest(POST, controllers.routes.AccountAuthoritiesController.revoke().url)
            .withJsonBody(Json.parse("""{"valid": "nope"}"""))

        running(app) {
          val result = route(app, invalidRequest).value
          status(result) mustBe BAD_REQUEST
        }
      }
    }

    "no EORINumber found in auth record" should {
      "return 403" in new Setup {
        when(mockAuthConnector.authorise[Enrolments](any, any)(any, any))
          .thenReturn(Future.successful(Enrolments(Set())))

        running(app) {
          val result = route(app, getRequestNoEori).value
          status(result) mustBe FORBIDDEN
          contentAsString(result) mustBe "Enrolment Identifier EORINumber not found"
        }
      }
    }
  }

  trait Setup {
    val traderEORI: EORI       = EORI("testEORI")
    val eoriJson: JsObject     = Json.obj("eori" -> traderEORI.value)
    val enrolments: Enrolments =
      Enrolments(
        Set(Enrolment(ENROLMENT_KEY, Seq(EnrolmentIdentifier(ENROLMENT_IDENTIFIER, traderEORI.value)), "activated"))
      )

    val fakeRequest: FakeRequest[AnyContentAsJson] = FakeRequest(POST, "/customs-financials-api/account-authorities")
      .withJsonBody(eoriJson)
      .withHeaders(CONTENT_TYPE -> "application/json")

    val requestWithEori = new RequestWithEori(traderEORI, fakeRequest)

    def getRequest(eori: Option[EORI]): RequestWithEori[AnyContentAsEmpty.type] = {
      val fakeRequest = FakeRequest(POST, "/customs-financials-api/account-authorities")
      new RequestWithEori(eori.getOrElse(traderEORI), fakeRequest)
    }

    val getRequestNoEori: RequestWithEori[AnyContentAsEmpty.type] = getRequest(None)

    val noAccountsForEoriMsg: String =
      """returned 400.
        | Response body: '{
        | "errorDetail":{
        | "timestamp":"2023-12-12",
        | "correlationId":"4abceddb-f7a0-4dce-a005-b68f4960fcf6",
        | "errorCode":"400",
        | "errorMessage":"Validation Error - Invalid Message",
        | "source":"Backend",
        | "sourceFaultDetail":{
        | "detail":["Bad Request : could not find accounts related to eori XI333186811548"]}}}""".stripMargin

    implicit val hc: HeaderCarrier    = HeaderCarrier()
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    def grantRequest(request: GrantAuthorityRequest): FakeRequest[AnyContentAsJson] =
      FakeRequest(POST, controllers.routes.AccountAuthoritiesController.grant().url)
        .withJsonBody(Json.toJson(request))

    def revokeRequest(request: RevokeAuthorityRequest): FakeRequest[AnyContentAsJson] =
      FakeRequest(POST, controllers.routes.AccountAuthoritiesController.revoke().url)
        .withJsonBody(Json.toJson(request))

    val mockAuthConnector: CustomAuthConnector               = mock[CustomAuthConnector]
    val mockAccountAuthorityService: AccountAuthorityService = mock[AccountAuthorityService]
    val mockAuthorisedRequest: AuthorisedRequest             = mock[AuthorisedRequest]

    when(mockAuthConnector.authorise[Enrolments](any, any)(any, any)).thenReturn(Future.successful(enrolments))

    val app: Application = GuiceApplicationBuilder()
      .overrides(
        inject.bind[CustomAuthConnector].toInstance(mockAuthConnector),
        inject.bind[AccountAuthorityService].toInstance(mockAccountAuthorityService)
      )
      .configure(
        "microservice.metrics.enabled" -> false,
        "metrics.enabled"              -> false,
        "auditing.enabled"             -> false
      )
      .build()
  }
}
