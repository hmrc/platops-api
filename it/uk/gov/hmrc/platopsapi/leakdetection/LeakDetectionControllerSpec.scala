package uk.gov.hmrc.platopsapi.leakdetection

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

class LeakDetectionControllerSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with GuiceOneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.leak-detection.host" -> wireMockHost,
      "microservice.services.leak-detection.port" -> wireMockPort)
    .build()

  private val controller = app.injector.instanceOf[LeakDetectionController]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  "LeakDetectionController" when {
    "processing webhook requests" should {
      "pass provided body and github signature header to leak-detection/validate" in {
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

        val result = controller.webhook()(fakeRequest)
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

      "not pass github signature header if one is not provided" in {
        stubFor(
          post(urlEqualTo("/leak-detection/validate"))
            .willReturn(
              aResponse()
                .withStatus(200)
            )
        )

        val fakeRequest = FakeRequest("POST", "/webhooks/leak-detection")
          .withBody(Json.parse("""{"some": "value"}"""))
          .withHeaders("Content-Type" -> "application/json")

        val result = controller.webhook()(fakeRequest)
        play.api.test.Helpers.status(result) shouldBe OK

        verify(
          postRequestedFor(urlEqualTo("/leak-detection/validate"))
            .withRequestBody(equalToJson("""{"some": "value"}"""))
            .withoutHeader("X-Hub-Signature-256")
        )
      }
    }
  }
}