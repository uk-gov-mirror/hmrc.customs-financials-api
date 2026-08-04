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

package connectors

import config.AppConfig
import config.MetaConfig.Platform.{MDTP, REGIME_CDS}
import domain.AccountWithAuthorities
import models.EORI
import models.requests.manageAuthorities.{
  AuthoritiesRequestCommon, AuthoritiesRequestDetail, StandingAuthoritiesRequest
}
import models.responses.StandingAuthoritiesResponse
import play.api.libs.ws.writeableOf_JsValue
import services.{DateTimeService, MetricsReporterService}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class Acc29Connector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig,
  dateTimeService: DateTimeService,
  metricsReporterService: MetricsReporterService,
  mdgHeaders: MdgHeaders
)(implicit executionContext: ExecutionContext) {

  def getStandingAuthorities(eori: EORI): Future[Seq[AccountWithAuthorities]] = {
    val commonRequest = AuthoritiesRequestCommon(
      REGIME_CDS,
      receiptDate = dateTimeService.currentDateTimeAsIso8601,
      acknowledgementReference = mdgHeaders.acknowledgementReference,
      MDTP
    )

    val standingAuthoritiesRequest =
      StandingAuthoritiesRequest(commonRequest, AuthoritiesRequestDetail(ownerEori = eori))

    metricsReporterService.withResponseTimeLogging("hods.post.get-standing-authority-details") {
      httpClient
        .post(url"${appConfig.acc29GetStandingAuthoritiesEndpoint}")(HeaderCarrier())
        .withBody[StandingAuthoritiesRequest](standingAuthoritiesRequest)
        .setHeader(mdgHeaders.headers(appConfig.acc29BearerToken, appConfig.acc29HostHeader): _*)
        .execute[StandingAuthoritiesResponse]
        .map { response =>
          val responseWithOwner = response.copy(
            accounts = response.accounts.map(_.copy(ownerEori = Some(response.ownerEori)))
          )
          responseWithOwner.accounts
        }
    }
  }
}
