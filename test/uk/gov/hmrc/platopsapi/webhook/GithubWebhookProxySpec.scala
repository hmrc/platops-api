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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global

class GithubWebhookProxySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with HttpClientV2Support {

  "GithubWebhookProxy" should {

    def stubbedResponse() = stubFor(
      post(urlEqualTo("/webhook"))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

    val onTest = new GithubWebhookProxy(httpClientV2)

    "pass provided body and github signature header to webhook request" in {
      stubbedResponse()

      val hc = new HeaderCarrier(otherHeaders = Seq(("X-Hub-Signature-256", "aaa111")))
      onTest.webhook(url"${wireMockUrl}/webhook", Json.parse("""{"some": "value"}"""))(hc).futureValue

      verify(
        postRequestedFor(urlEqualTo("/webhook"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withHeader("X-Hub-Signature-256", equalTo("aaa111"))
      )
    }

    "not pass github signature header if one is not provided in the hc" in {
      stubbedResponse()

      val hc = new HeaderCarrier()
      onTest.webhook(url"${wireMockUrl}/webhook", Json.parse("""{"some": "value"}"""))(hc).futureValue

      verify(
        postRequestedFor(urlEqualTo("/webhook"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withoutHeader("X-Hub-Signature-256")
      )
    }
  }
}