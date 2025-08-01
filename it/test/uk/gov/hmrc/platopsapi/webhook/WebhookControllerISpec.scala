/*
 * Copyright 2025 HM Revenue & Customs
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

import org.apache.commons.codec.digest.{HmacAlgorithms, HmacUtils}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.ws.WSClient
import play.api.test.Helpers
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.platopsapi.persistence.GithubRequestsQueueRepository

class WebhookControllerISpec extends AnyWordSpec
  with Matchers
  with GuiceOneServerPerSuite
  with ScalaFutures
  with DefaultPlayMongoRepositorySupport[WorkItem[GithubWebhookRequest]]:

  implicit val timout: Timeout = Helpers.defaultAwaitTimeout
  private val webhookSecretKey = "1234"

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "github.webhookSecretKey" -> webhookSecretKey,
      "mongodb.uri"             -> mongoUri
    ).build()

  implicit val mat       : Materializer                  = app.injector.instanceOf[Materializer]
  override val repository: GithubRequestsQueueRepository = app.injector.instanceOf[GithubRequestsQueueRepository]
  private  val wsClient  : WSClient                      = app.injector.instanceOf[WSClient]
  private  val controller: WebhookController             = app.injector.instanceOf[WebhookController]
  private  val baseUrl   : String                        = s"http://localhost:$port"


  private val jsonPayload  : JsObject = Json.obj("foo" -> "bar")
  private val payloadString: String   = Json.stringify(jsonPayload)
  private val ghSignature  : String   = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, webhookSecretKey).hmacHex(payloadString)

  "POST /webhook" should:
    "process Github Pull event" in:
      val pullEvent = WebhookEvent.Pull
      val response  = wsClient
                        .url(s"$baseUrl/webhook")
                        .addHttpHeaders(
                          "X-GitHub-Event"      -> pullEvent.asString
                        , "X-Hub-Signature-256" -> ghSignature
                        , "Content-Type"        -> "application/json"
                        )
                        .post(Json.toJson(jsonPayload))
                        .futureValue
      response.status shouldBe 202

      findAll().futureValue.map(_.item) should contain theSameElementsAs
        List(
          GithubWebhookRequest(pullEvent, "http://localhost:8856/pr-commenter/webhook"          , payloadString)
        , GithubWebhookRequest(pullEvent, "http://localhost:9015/teams-and-repositories/webhook", payloadString)
        )

    "process Github Push event" in:
      val pushEvent = WebhookEvent.Push
      val response  = wsClient
                        .url(s"$baseUrl/webhook")
                        .addHttpHeaders(
                          "X-GitHub-Event"      -> pushEvent.asString
                        , "X-Hub-Signature-256" -> ghSignature
                        , "Content-Type"        -> "application/json"
                        )
                        .post(Json.toJson(jsonPayload))
                        .futureValue
      response.status shouldBe 202

      findAll().futureValue.map(_.item) should contain theSameElementsAs
        List(
           GithubWebhookRequest(pushEvent, "http://localhost:8460/service-configs/webhook"       , payloadString)
         , GithubWebhookRequest(pushEvent, "http://localhost:9015/teams-and-repositories/webhook", payloadString)
         , GithubWebhookRequest(pushEvent, "http://localhost:8855/leak-detection/validate"       , payloadString)
        )

    "process Github Repository event" in:
      val repositoryEvent = WebhookEvent.Repository
      val response        = wsClient
                              .url(s"$baseUrl/webhook")
                              .addHttpHeaders(
                                "X-GitHub-Event"      -> repositoryEvent.asString
                              , "X-Hub-Signature-256" -> ghSignature
                              , "Content-Type"        -> "application/json"
                              )
                              .post(Json.toJson(jsonPayload))
                              .futureValue
      response.status shouldBe 202

      findAll().futureValue.map(_.item) should contain theSameElementsAs
        List(
          GithubWebhookRequest(repositoryEvent, "http://localhost:9015/teams-and-repositories/webhook", payloadString)
        , GithubWebhookRequest(repositoryEvent, "http://localhost:8855/leak-detection/validate"       , payloadString)
        )

    "process Github Team event" in:
      val teamEvent = WebhookEvent.Team
      val response  = wsClient
                        .url(s"$baseUrl/webhook")
                        .addHttpHeaders(
                          "X-GitHub-Event"      -> teamEvent.asString
                        , "X-Hub-Signature-256" -> ghSignature
                        , "Content-Type"        -> "application/json"
                        )
                        .post(Json.toJson(jsonPayload))
                        .futureValue
      response.status shouldBe 202

      findAll().futureValue.map(_.item) should contain theSameElementsAs
              List(
                GithubWebhookRequest(teamEvent, "http://localhost:9015/teams-and-repositories/webhook", payloadString)
              , GithubWebhookRequest(teamEvent, "http://localhost:8470/internal-auth/webhook"         , payloadString)
              )

    "process Github Ping event" in:
      val pingEvent = WebhookEvent.Ping
      val response  = wsClient
                        .url(s"$baseUrl/webhook")
                        .addHttpHeaders(
                          "X-GitHub-Event"      -> pingEvent.asString
                        , "X-Hub-Signature-256" -> ghSignature
                        , "Content-Type"        -> "application/json"
                        )
                        .post(Json.toJson(jsonPayload))
                        .futureValue
      response.status shouldBe 202

      findAll().futureValue should contain theSameElementsAs List.empty

    "return 400 when Github event is invalid" in:
      val response = wsClient
                      .url(s"$baseUrl/webhook")
                      .addHttpHeaders(
                        "X-GitHub-Event"      -> ""
                      , "X-Hub-Signature-256" -> ghSignature
                      , "Content-Type"        -> "application/json"
                      )
                      .post(Json.toJson(jsonPayload))
                      .futureValue
      response.status shouldBe 400

    "return 400 when Github event type not specified" in:
      val response = wsClient
                       .url(s"$baseUrl/webhook")
                       .addHttpHeaders(
                         "X-Hub-Signature-256" -> ghSignature
                       , "Content-Type"        -> "application/json"
                       )
                       .post(Json.toJson(jsonPayload))
                       .futureValue
      response.status shouldBe 400

    "return 400 when Github signature is invalid" in:
      val response  = wsClient
                        .url(s"$baseUrl/webhook")
                        .addHttpHeaders(
                          "X-GitHub-Event"      -> WebhookEvent.Team.asString
                        , "X-Hub-Signature-256" -> ""
                        , "Content-Type"        -> "application/json"
                        )
                        .post(Json.toJson(jsonPayload))
                        .futureValue
      response.status shouldBe 400

    "return 400 when Github signature not found in headers" in:
      val response = wsClient
                       .url(s"$baseUrl/webhook")
                       .addHttpHeaders(
                         "X-GitHub-Event" -> WebhookEvent.Team.asString
                       , "Content-Type"   -> "application/json"
                       )
                       .post(Json.toJson(jsonPayload))
                       .futureValue
      response.status shouldBe 400
