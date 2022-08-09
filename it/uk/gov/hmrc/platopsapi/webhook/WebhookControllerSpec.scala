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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.test.WireMockSupport

class WebhookControllerSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with GuiceOneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.leak-detection.host" -> wireMockHost,
      "microservice.services.leak-detection.port" -> wireMockPort,
      "microservice.services.pr-commenter.host" -> wireMockHost,
      "microservice.services.pr-commenter.port" -> wireMockPort)
    .build()

  private val controller = app.injector.instanceOf[WebhookController]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  "WebhookController" should {
    "process a leak-detection webhook request" in {
      stubFor(
        post(urlEqualTo("/leak-detection/validate"))
          .withHeader("X-Hub-Signature-256", equalTo("aaa111"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """
                  |{
                  |  "details": "Request successfully queued"
                  |}""".stripMargin))
      )

      val fakeRequest = FakeRequest("POST", "/webhooks/leak-detection")
        .withBody(Json.parse("""{"some": "value"}"""))
        .withHeaders("Content-Type" -> "application/json", "X-Hub-Signature-256" -> "aaa111")

      val result = controller.leakDetection()(fakeRequest)
      play.api.test.Helpers.status(result) shouldBe OK
      contentAsString(result) shouldBe
        """
          |{
          |  "details": "Request successfully queued"
          |}""".stripMargin

      verify(
        postRequestedFor(urlEqualTo("/leak-detection/validate"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withHeader("X-Hub-Signature-256", equalTo("aaa111"))
      )
    }

    "process a pr-commenter webhook request" in {
      stubFor(
        post(urlEqualTo("/pr-commenter/webhook"))
          .withHeader("X-Hub-Signature-256", equalTo("aaa111"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """
                  |{
                  |  "details": "Request successfully queued"
                  |}""".stripMargin))
      )

      val fakeRequest = FakeRequest("POST", "/webhooks/pr-commenter")
        .withBody(Json.parse("""{"some": "value"}"""))
        .withHeaders("Content-Type" -> "application/json", "X-Hub-Signature-256" -> "aaa111")

      val result = controller.prCommenter()(fakeRequest)
      play.api.test.Helpers.status(result) shouldBe OK
      contentAsString(result) shouldBe
        """
          |{
          |  "details": "Request successfully queued"
          |}""".stripMargin

      verify(
        postRequestedFor(urlEqualTo("/pr-commenter/webhook"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withHeader("X-Hub-Signature-256", equalTo("aaa111"))
      )
    }
  }
}