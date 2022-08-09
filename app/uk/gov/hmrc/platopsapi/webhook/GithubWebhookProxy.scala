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

package uk.gov.hmrc.platopsapi.webhook

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.HttpEntity
import play.api.libs.json.JsValue
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GithubWebhookProxy @Inject()(httpClientV2: HttpClientV2)(implicit val ec: ExecutionContext) {

  def webhook(url: URL, body: JsValue)(implicit hc: HeaderCarrier): Future[Result] = {
    webhookRequestWithSignatureHeader(url)
      .withBody(body)
      .execute
      .map(response =>
        Result(
          ResponseHeader(response.status, response.headers.flatMap(x => x._2.map(y => x._1 -> y))),
          HttpEntity.Streamed(response.bodyAsSource, None, response.header(CONTENT_TYPE))
        )
      )
  }

  private def webhookRequestWithSignatureHeader(url: URL)(implicit hc: HeaderCarrier): RequestBuilder = {
    val req = httpClientV2.post(url)
    hc.otherHeaders.filter(_._1 == "X-Hub-Signature-256").headOption match {
      case Some(h) => req.setHeader(h)
      case _ => req
    }
  }
}
