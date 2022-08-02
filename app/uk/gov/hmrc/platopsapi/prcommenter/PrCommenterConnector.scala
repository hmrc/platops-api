/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.platopsapi.prcommenter

import play.api.libs.json.JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PrCommenterConnector @Inject()(
                                      httpClientV2: HttpClientV2,
                                      servicesConfig: ServicesConfig
                                    )(implicit val ec: ExecutionContext) {

  lazy private val baseUrl = servicesConfig.baseUrl("pr-commenter")

  def webhook(body: JsValue)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val req = httpClientV2.post(url"${baseUrl}/pr-commenter/webhook")
      .withBody(body)

    hc.otherHeaders.filter(_._1 == "X-Hub-Signature-256").headOption match {
      case Some(h) => req.setHeader(h).execute[JsValue]
      case _ => req.execute[JsValue]
    }
  }
}
