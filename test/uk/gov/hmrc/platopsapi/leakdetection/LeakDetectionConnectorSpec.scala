package uk.gov.hmrc.platopsapi.leakdetection

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class LeakDetectionConnectorSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with HttpClientV2Support {

  "LeakDetectionConnector" should {

    def stubbedResponse() = stubFor(
      post(urlEqualTo("/leak-detection/validate"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """
                |{
                |  "details": "Request successfully queued"
                |}""".stripMargin))
    )

    val configuration = Configuration(
      "microservice.services.leak-detection.host" -> wireMockHost,
      "microservice.services.leak-detection.port" -> wireMockPort
    )

    val onTest = new LeakDetectionConnector(httpClientV2, new ServicesConfig(configuration))

    "pass provided body and github signature header to webhook request" in {
      stubbedResponse()

      val hc = new HeaderCarrier(otherHeaders = Seq(("X-Hub-Signature-256", "aaa111")))

      val result = onTest.webhook(Json.parse("""{"some": "value"}"""))(hc).futureValue

      result shouldBe Json.parse("""{"details": "Request successfully queued"}""")

      verify(
        postRequestedFor(urlEqualTo("/leak-detection/validate"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withHeader("X-Hub-Signature-256", equalTo("aaa111"))
      )
    }

    "do not pass github signature header if one is not provided in the hc" in {
      stubbedResponse()

      val hc = new HeaderCarrier()
      val result = onTest.webhook(Json.parse("""{"some": "value"}"""))(hc).futureValue

      result shouldBe Json.parse("""{"details": "Request successfully queued"}""")

      verify(
        postRequestedFor(urlEqualTo("/leak-detection/validate"))
          .withRequestBody(equalToJson("""{"some": "value"}"""))
          .withoutHeader("X-Hub-Signature-256")
      )
    }
  }
}