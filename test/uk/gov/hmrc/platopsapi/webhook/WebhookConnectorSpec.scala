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

package uk.gov.hmrc.platopsapi.webhook

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class WebhookConnectorSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with HttpClientV2Support:

  "WebhookConnectorSpec" should:

    def stubbedResponse() = stubFor(
      post(urlEqualTo("/webhook"))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

    val onTest = new WebhookConnector(httpClientV2, Configuration("internal-auth.token" -> "token"))

    "pass provided body and github event header to webhook request" in:
      stubbedResponse()
      onTest.webhook(s"$wireMockUrl/webhook", """{"some": "value"}""", WebhookEvent.Push)(HeaderCarrier()).futureValue
      verify(
        postRequestedFor(urlEqualTo("/webhook"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withHeader("X-GitHub-Event", equalTo("push"))
          .withHeader("Authorization", equalTo("token"))
      )

    "not pass github signature header to webhook request" in:
      stubbedResponse()
      val hc = new HeaderCarrier(otherHeaders = Seq(("X-Hub-Signature-256", "aaa111")))
      onTest.webhook(s"$wireMockUrl/webhook", """{"some": "value"}""", WebhookEvent.Push)(hc).futureValue
      verify(
        postRequestedFor(urlEqualTo("/webhook"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withoutHeader("X-Hub-Signature-256")
      )
