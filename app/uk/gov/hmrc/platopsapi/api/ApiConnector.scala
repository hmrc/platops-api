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

package uk.gov.hmrc.platopsapi.api

import play.api.libs.ws.BodyWritable
import play.api.mvc.Result
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.platopsapi.ConnectorUtil

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import izumi.reflect.Tag

@Singleton
class ApiConnector @Inject()(httpClientV2: HttpClientV2)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits.readRaw

  def get(url: URL)(implicit hc: HeaderCarrier): Future[Result] =
    httpClientV2
      .get(url)
      .execute
      .map(ConnectorUtil.toResult)

  def post[B : BodyWritable: Tag](url: URL, body: B)(implicit hc: HeaderCarrier): Future[Result] =
    httpClientV2
      .post(url)
      .withBody(body)
      .execute
      .map(ConnectorUtil.toResult)
}
