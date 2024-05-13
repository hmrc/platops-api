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

package uk.gov.hmrc.platopsapi.api

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json._
import play.api.libs.ws.WSClient
import stub.TestStubs

class ApiControllerISpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"

  override def beforeAll(): Unit = {
     TestStubs.resetAll()
     super.beforeAll()
  }

  "GET /api/v2/decommissioned-repositories" should {
    "return a list of all decommissioned repositories" in {
      val response = wsClient
        .url(s"$baseUrl/api/v2/decommissioned-repositories")
        .get()
        .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """[{"repoName":"repo-1", "repoType": "Service"},{"repoName":"repo-3", "repoType": "Service"},{"repoName":"repo-4","repoType": "Library"},{"repoName":"repo-5","repoType": "Other"}]"""
      )
    }

    "return a list of decommissioned services" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/v2/decommissioned-repositories?repoType=Service")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """[{"repoName":"repo-1", "repoType": "Service"},{"repoName":"repo-3", "repoType": "Service"}]"""
      )
    }
  }

  "GET /api/v2/teams" should {
    "return a list of team summaries" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/v2/teams")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """[
          |  {
          |    "name": "Team A",
          |    "lastActiveDate": "2024-05-08T16:24:37Z",
          |    "repos": [
          |      "repo-1",
          |      "repo-2"
          |    ]
          |  },
          |  {
          |    "name": "Team B",
          |    "repos": []
          |  }
          |]""".stripMargin
      )
    }
  }
}
