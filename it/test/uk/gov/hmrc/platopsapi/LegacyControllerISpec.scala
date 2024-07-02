/*
 * Copyright 2024 HM Revenue & Customs
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

/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.DoNotDiscover
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.libs.ws.writeableOf_JsValue
import play.api.libs.ws.writableOf_Source
import play.api.libs.ws.writeableOf_String
import uk.gov.hmrc.platopsapi.ResourceUtil.fromResource

@DoNotDiscover
class LegacyControllerISpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val wsClient     = app.injector.instanceOf[WSClient]
  private val baseUrl      = s"http://localhost:$port"

  "GET /releases-api/whats-running-where" should {
    "return whats running where data when redirected to /api/whats-running-where" in {
      val response =
        wsClient
          .url(s"$baseUrl/releases-api/whats-running-where")
          .get()
          .futureValue

      response.status       shouldBe 200
      response.uri.toString shouldBe s"$baseUrl/api/whats-running-where"
      response.json         shouldBe Json.parse(fromResource("/expectedJson/whatsRunningWhere.json"))
    }
  }

  "GET /releases-api/whats-running-where/:serviceName" should {
    "return what's running where data for a single service when redirect to /api/whats-running-where/:serviceName" in {
      val response =
        wsClient
          .url(s"$baseUrl/releases-api/whats-running-where/catalogue-frontend")
          .get()
          .futureValue

      response.status       shouldBe 200
      response.uri.toString shouldBe s"$baseUrl/api/whats-running-where/catalogue-frontend"
      response.json         shouldBe Json.parse(fromResource("/expectedJson/whatsRunningWhereForService.json"))
    }
  }

  "POST /slack-notifications/v2/notifications" should {
    "return msgId when the slack notification json sent is valid" in {
      val response =
        wsClient
          .url(s"$baseUrl/slack-notifications/v2/notification")
          .withHttpHeaders("Authorization" -> "token")
          .post(slackMessageBody)
          .futureValue

      response.status                          shouldBe 202
      (response.json \ "msgId").asOpt[JsValue] shouldBe defined
    }

    "return 400 when the slack notification json sent is invalid" in {
      val response =
        wsClient
          .url(s"$baseUrl/slack-notifications/v2/notification")
          .withHttpHeaders("Authorization" -> "token")
          .post(Json.parse("""{}"""))
          .futureValue

      response.status shouldBe 400
    }

    "return 401 when requesting client is unauthorised" in {
      val response =
        wsClient
          .url(s"$baseUrl/slack-notifications/v2/notification")
          .withHttpHeaders("Authorization" -> "no-token")
          .post(slackMessageBody)
          .futureValue

      response.status shouldBe 401
      response.json   shouldBe Json.parse("""{"statusCode":401,"message":"Unauthorized"}""")
    }

    "return 403 when requesting client is authorised but does not have the correct permissions" in {
      val response =
        wsClient
          .url(s"$baseUrl/slack-notifications/v2/notification")
          .withHttpHeaders("Authorization" -> "no-permissions")
          .post(slackMessageBody)
          .futureValue

      response.status shouldBe 403
      response.json   shouldBe Json.parse("""{"statusCode":403,"message":"Forbidden"}""")
    }
  }

  "GET /slack-notifications/v2/:msgId/status" should {
    def sendSlackMessageResponse() = {
      val response = wsClient
        .url(s"$baseUrl/slack-notifications/v2/notification")
        .withHttpHeaders("Authorization" -> "token")
        .post(slackMessageBody)
        .futureValue

      (response.json \ "msgId").as[String]
    }

    def getStatus(msgId: String, authToken: String) =
      wsClient
        .url(s"$baseUrl/slack-notifications/v2/$msgId/status")
        .withHttpHeaders("Authorization" -> authToken)
        .get()
        .futureValue

    "return 200 for valid message id in status request" in {
      val msgId          = sendSlackMessageResponse()
      val statusResponse = getStatus(msgId, "token")
      statusResponse.status shouldBe 200
    }

    "return 401 when requesting client is unauthorised" in {
      val msgId          = sendSlackMessageResponse()
      val statusResponse = getStatus(msgId, "no-token")
      statusResponse.status shouldBe 401
    }

    "return 403 when requesting client is authorised but does not have the correct permissions" in {
      val msgId          = sendSlackMessageResponse()
      val statusResponse = getStatus(msgId, "no-permissions")
      statusResponse.status shouldBe 403
    }
  }

  private val slackMessageBody =
    Json.parse(
    """
      |{
      |  "displayName": "test",
      |  "emoji": ":see_no_evil:",
      |  "text": "test",
      |  "channelLookup": {
      |    "by": "github-team",
      |    "teamName": "platops"
      |  },
      |  "blocks": [
      |    {
      |      "type": "section",
      |      "text": {
      |        "type": "mrkdwn",
      |        "text": "Testing API"
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin
    )
}
