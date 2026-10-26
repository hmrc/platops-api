/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.platopsapi.stub

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.{JsArray, JsValue, Json}
import uk.gov.hmrc.platopsapi.ResourceUtil.fromResource

object ApiStubs:
  private def fixture(name: String): JsValue =
    Json.parse(fromResource(s"/expectedJson/$name.json"))

  private def respond(path: String, body: JsValue): Unit =
    stubFor(get(urlEqualTo(path)).willReturn(
      aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(Json.stringify(body))
    ))

  private def repos(filter: JsValue => Boolean): JsArray =
    JsArray(fixture("repositoriesV2").as[JsArray].value.filter(filter))

  def stubAll(): Unit =
    val all = fixture("repositoriesV2")
    respond("/api/v2/repositories", all)
    respond("/api/v2/repositories?name=repo-7", repos(r => (r \ "name").as[String] == "repo-7"))
    respond("/api/v2/repositories?team=PlatOps", repos(r => (r \ "teamNames").as[Seq[String]].contains("PlatOps")))
    respond("/api/v2/repositories?owningTeam=TestTeam", repos(r => (r \ "owningTeams").as[Seq[String]].contains("TestTeam")))
    respond("/api/v2/repositories?archived=true&repoType=Service&serviceType=backend&tag=api",
      repos(r => (r \ "isArchived").as[Boolean] && (r \ "repoType").as[String] == "Service" &&
        (r \ "serviceType").as[String] == "backend" && (r \ "tags").as[Seq[String]].contains("api")))

    val decommissioned =
      Json.parse(
      """
          |[
          |  {
          |    "repoName":"repo-1",
          |    "repoType":"Service"
          |  },
          |  {
          |    "repoName":"repo-3",
          |    "repoType":"Service"
          |  },
          |  {
          |    "repoName":"repo-4",
          |    "repoType":"Library"
          |  },
          |  {
          |    "repoName":"repo-5",
          |    "repoType":"Other"
          |  }
          |]""".stripMargin)

    respond("/api/v2/decommissioned-repositories", decommissioned)
    respond("/api/v2/decommissioned-repositories?repoType=Service",
      JsArray(decommissioned.as[JsArray].value.filter(r => (r \ "repoType").as[String] == "Service")))
    respond("/api/v2/teams", Json.parse(fromResource("/seedCollectionsJson/teamSummaries.json")))
    respond("/api/repositories/catalogue-frontend/latest-version", fixture("latestRepositoryVersion"))
    respond("/releases-api/whats-running-where", fixture("whatsRunningWhere"))
    respond("/releases-api/whats-running-where/catalogue-frontend", fixture("whatsRunningWhereForService"))

    val notificationPath = "/slack-notifications/v2/notification"
    val statusPath       = "/slack-notifications/v2/test-message-id/status"

    stubFor(post(urlEqualTo(notificationPath))
      .atPriority(1)
      .withHeader("Authorization", equalTo("no-token"))
      .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json")
        .withBody("""{"statusCode":401,"message":"Unauthorized"}""")))
    stubFor(post(urlEqualTo(notificationPath))
      .atPriority(1)
      .withHeader("Authorization", equalTo("no-permissions"))
      .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
        .withBody("""{"statusCode":403,"message":"Forbidden"}""")))
    stubFor(post(urlEqualTo(notificationPath))
      .atPriority(1)
      .withRequestBody(equalToJson("{}"))
      .willReturn(aResponse().withStatus(400)))
    stubFor(post(urlEqualTo(notificationPath))
      .atPriority(10)
      .willReturn(aResponse().withStatus(202).withHeader("Content-Type", "application/json")
        .withBody("""{"msgId":"test-message-id"}""")))

    stubFor(get(urlEqualTo(statusPath))
      .atPriority(1)
      .withHeader("Authorization", equalTo("no-token"))
      .willReturn(aResponse().withStatus(401)))
    stubFor(get(urlEqualTo(statusPath))
      .atPriority(1)
      .withHeader("Authorization", equalTo("no-permissions"))
      .willReturn(aResponse().withStatus(403)))
    stubFor(get(urlEqualTo(statusPath))
      .atPriority(10)
      .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
        .withBody("""{"status":"sent"}""")))
