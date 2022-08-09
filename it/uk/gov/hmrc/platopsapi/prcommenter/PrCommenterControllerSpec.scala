package uk.gov.hmrc.platopsapi.prcommenter

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

class PrCommenterControllerSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with GuiceOneAppPerSuite {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.pr-commenter.host" -> wireMockHost,
      "microservice.services.pr-commenter.port" -> wireMockPort)
    .build()

  private val controller = app.injector.instanceOf[PrCommenterController]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  "PrCommenterController" when {
    "processing webhook requests" should {
      "pass provided body and github signature header to pr-commenter/webhook" in {
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

        val result = controller.webhook()(fakeRequest)
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

      "not pass github signature header if one is not provided" in {
        stubFor(
          post(urlEqualTo("/pr-commenter/webhook"))
            .willReturn(
              aResponse()
                .withStatus(200)
            )
        )

        val fakeRequest = FakeRequest("POST", "/webhooks/pr-commenter")
          .withBody(Json.parse("""{"some": "value"}"""))
          .withHeaders("Content-Type" -> "application/json")

        val result = controller.webhook()(fakeRequest)
        play.api.test.Helpers.status(result) shouldBe OK

        verify(
          postRequestedFor(urlEqualTo("/pr-commenter/webhook"))
            .withRequestBody(equalToJson("""{"some": "value"}"""))
            .withoutHeader("X-Hub-Signature-256")
        )
      }
    }
  }
}