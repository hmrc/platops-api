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

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.commons.codec.digest.{HmacAlgorithms, HmacUtils}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.test.WireMockSupport

class WebhookControllerSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with GuiceOneAppPerSuite {

  implicit val timout = Helpers.defaultNegativeTimeout.t

  private val webhookSecretKey = "1234"

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "github.webhookSecretKey"                    -> webhookSecretKey,
      "microservice.services.leak-detection.host"  -> wireMockHost,
      "microservice.services.leak-detection.port"  -> wireMockPort,
      "microservice.services.pr-commenter.host"    -> wireMockHost,
      "microservice.services.pr-commenter.port"    -> wireMockPort,
      "microservice.services.service-configs.host" -> wireMockHost,
      "microservice.services.service-configs.port" -> wireMockPort
    ).build()

  private val controller = app.injector.instanceOf[WebhookController]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  "WebhookController" should {
    "process a pull request" in {
      val eventType   = "pull_request"
      val payload     = """{"foo":"bar"}"""
      val ghSignature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, webhookSecretKey).hmacHex(payload)

      stubFor(
        post(urlEqualTo("/pr-commenter/webhook"))
          .withHeader("X-GitHub-Event", equalTo(eventType))
          .willReturn(aResponse().withStatus(200)))

      val result = controller.processGithubWebhook()(
        FakeRequest("POST", "/webhook")
          .withHeaders(
            "X-GitHub-Event"      -> eventType,
            "X-Hub-Signature-256" -> ghSignature
          ).withBody(payload)
      )
      Helpers.status(result)        shouldBe Helpers.OK
      Helpers.contentAsJson(result) shouldBe Json.obj("details" -> "Event type 'pull_request' processed")

      verify(
        postRequestedFor(urlEqualTo("/pr-commenter/webhook"))
          .withRequestBody(equalToJson(payload)))
    }

    "process a push" in {
      val eventType   = "push"
      val payload     = """{"foo":"bar"}"""
      val ghSignature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, webhookSecretKey).hmacHex(payload)

      stubFor(
        post(urlEqualTo("/leak-detection/validate"))
          .withHeader("X-GitHub-Event", equalTo(eventType))
          .willReturn(aResponse().withStatus(200)))

      stubFor(
        post(urlEqualTo("/service-configs/webhook"))
          .withHeader("X-GitHub-Event", equalTo(eventType))
          .willReturn(aResponse().withStatus(200)))

      val result = controller.processGithubWebhook()(
        FakeRequest("POST", "/webhook")
          .withHeaders(
            "X-GitHub-Event"      -> eventType,
            "X-Hub-Signature-256" -> ghSignature
          ).withBody(payload)
      )
      Helpers.status(result)        shouldBe Helpers.OK
      Helpers.contentAsJson(result) shouldBe Json.obj("details" -> "Event type 'push' processed")

      verify(
        postRequestedFor(urlEqualTo("/leak-detection/validate"))
          .withHeader("X-GitHub-Event", equalTo(eventType))
          .withRequestBody(equalToJson(payload)))

      verify(
        postRequestedFor(urlEqualTo("/service-configs/webhook"))
          .withHeader("X-GitHub-Event", equalTo(eventType))
          .withRequestBody(equalToJson(payload)))
    }

    "process a repository" in {
      val eventType   = "repository"
      val payload     = """{"foo":"bar"}"""
      val ghSignature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, webhookSecretKey).hmacHex(payload)

      stubFor(
        post(urlEqualTo("/leak-detection/validate"))
          .withHeader("X-GitHub-Event", equalTo(eventType))
          .willReturn(aResponse().withStatus(200)))

      val result = controller.processGithubWebhook()(
        FakeRequest("POST", "/webhook")
          .withHeaders(
            "X-GitHub-Event"      -> eventType,
            "X-Hub-Signature-256" -> ghSignature
          ).withBody(payload)
      )
      Helpers.status(result)        shouldBe Helpers.OK
      Helpers.contentAsJson(result) shouldBe Json.obj("details" -> "Event type 'repository' processed")

      verify(
        postRequestedFor(urlEqualTo("/leak-detection/validate"))
          .withRequestBody(equalToJson(payload)))
    }
  }
}
