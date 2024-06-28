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

package uk.gov.hmrc.platopsapi

import play.api.http.HttpEntity
import play.api.http.HeaderNames.{CONTENT_TYPE, CONTENT_LENGTH}
import play.api.mvc.{ResponseHeader, Result}

import uk.gov.hmrc.http.HttpResponse

object ConnectorUtil {

  def toResult[A](rsp: HttpResponse) =
    Result(
      ResponseHeader(
        rsp.status,
        (rsp.headers - CONTENT_TYPE - CONTENT_LENGTH).flatMap { (k, vs) => vs.map(k -> _) }
      )
    , HttpEntity.Streamed(rsp.bodyAsSource, None, None)
    )
}
