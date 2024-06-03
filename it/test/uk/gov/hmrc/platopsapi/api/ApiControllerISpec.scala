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
import uk.gov.hmrc.platopsapi.stub.TestStubs

import java.io.File
import scala.io.Source
import scala.util.Using

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

  "GET /api/v2/repositories" should {
    "return a list of all repositories" in {
      val response = wsClient
        .url(s"$baseUrl/api/v2/repositories")
        .get()
        .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(fromResource("repositoriesV2.json"))
    }

    "return a repository by name" in {
      val response = wsClient
        .url(s"$baseUrl/api/v2/repositories?name=repo-7")
        .get()
        .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """
          |[
          |  {
          |    "name": "repo-7",
          |    "description": "service",
          |    "url": "https://github.com/hmrc/repo-7",
          |    "createdDate": "2014-03-11T19:59:49Z",
          |    "lastActiveDate": "2020-12-03T12:57:55Z",
          |    "isPrivate": false,
          |    "repoType": "Service",
          |    "serviceType": "backend",
          |    "tags": [
          |      "api"
          |    ],
          |    "digitalServiceName": "",
          |    "owningTeams": [
          |      "TestTeam"
          |    ],
          |    "language": "Scala",
          |    "isArchived": false,
          |    "defaultBranch": "main",
          |    "isDeprecated": false,
          |    "teamNames": [
          |      "TestTeam"
          |    ]
          |  }
          |]""".stripMargin
      )
    }

    "return list of repositories associated with a team" in {
      val team     = "PlatOps"
      val response = wsClient
        .url(s"$baseUrl/api/v2/repositories?team=$team")
        .get()
        .futureValue

      val res = response.json.as[Seq[JsObject]]

      response.status shouldBe 200
      res.size        shouldBe 6
      res.forall(obj => (obj \ "teamNames").as[Seq[String]].contains(team))
      res.head        shouldBe Json.parse(
        """
          |{
          |  "name": "repo-1",
          |  "description": "archived",
          |  "url": "https://github.com/hmrc/repo-1",
          |  "createdDate": "2014-03-11T19:59:49Z",
          |  "lastActiveDate": "2020-12-03T12:57:55Z",
          |  "isPrivate": false,
          |  "repoType": "Service",
          |  "serviceType": "backend",
          |  "tags": [
          |    "api"
          |  ],
          |  "owningTeams": [
          |    "PlatOps"
          |  ],
          |  "language": "Scala",
          |  "isArchived": true,
          |  "defaultBranch": "main",
          |  "isDeprecated": true,
          |  "teamNames": [
          |    "PlatOps"
          |  ]
          |}
          |""".stripMargin
      )
    }

    "return list of repositories by an owning team" in {
      val team = "TestTeam"
      val response = wsClient
        .url(s"$baseUrl/api/v2/repositories?owningTeam=$team")
        .get()
        .futureValue

      val res = response.json.as[Seq[JsObject]]

      response.status shouldBe 200
      res.size        shouldBe 1
      res.forall(obj => (obj \ "owningTeams").as[Seq[String]].contains(team))
      res.head        shouldBe Json.parse(
        """
          |{
          |  "name": "repo-7",
          |  "description": "service",
          |  "url": "https://github.com/hmrc/repo-7",
          |  "createdDate": "2014-03-11T19:59:49Z",
          |  "lastActiveDate": "2020-12-03T12:57:55Z",
          |  "isPrivate": false,
          |  "repoType": "Service",
          |  "serviceType": "backend",
          |  "tags": [
          |    "api"
          |  ],
          |  "digitalServiceName": "",
          |  "owningTeams": [
          |    "TestTeam"
          |  ],
          |  "language": "Scala",
          |  "isArchived": false,
          |  "defaultBranch": "main",
          |  "isDeprecated": false,
          |  "teamNames": [
          |    "TestTeam"
          |  ]
          |}
          |""".stripMargin
      )
    }

    "return archived repositories that are backend services with the api tag" in {
      val response = wsClient
        .url(s"$baseUrl/api/v2/repositories?archived=true&repoType=Service&serviceType=backend&tag=api")
        .get()
        .futureValue

      val res = response.json.as[Seq[JsObject]]

      response.status shouldBe 200
      res.size        shouldBe 1
      res.head        shouldBe Json.parse(
        """
          |{
          |  "name": "repo-1",
          |  "description": "archived",
          |  "url": "https://github.com/hmrc/repo-1",
          |  "createdDate": "2014-03-11T19:59:49Z",
          |  "lastActiveDate": "2020-12-03T12:57:55Z",
          |  "isPrivate": false,
          |  "repoType": "Service",
          |  "serviceType": "backend",
          |  "tags": [
          |    "api"
          |  ],
          |  "owningTeams": [
          |    "PlatOps"
          |  ],
          |  "language": "Scala",
          |  "isArchived": true,
          |  "defaultBranch": "main",
          |  "isDeprecated": true,
          |  "teamNames": [
          |    "PlatOps"
          |  ]
          |}
          |""".stripMargin
      )
    }
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
        """
          |[
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
          |]
          |""".stripMargin
      )
    }
  }

  "GET /api/teams_with_repositories" should {
    "return a list of teams and their repositories" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/teams_with_repositories")
          .get()
          .futureValue

      val expectedJson       = Json.parse(fromResource("teamRepositories.json"))

      val actualNames        = (response.json \\ "name").map(_.as[String])
      val expectedNames      = (expectedJson  \\ "name").map(_.as[String])

      val actualRepos        = (response.json \\ "repos").map(_.as[JsObject])
      val expectedRepos      = (expectedJson  \\ "repos").map(_.as[JsObject])

      val actualOwnedRepos   = (response.json \\ "ownedRepos").map(_.as[Seq[String]])
      val expectedOwnedRepos = (response.json \\ "ownedRepos").map(_.as[Seq[String]])

       response.status shouldBe 200
       actualRepos     shouldBe expectedRepos

       actualNames      should contain theSameElementsAs expectedNames
       actualOwnedRepos should contain theSameElementsAs expectedOwnedRepos


//      response.json   shouldBe Json.parse(fromResource("teamRepositories.json"))
    }
  }

  "GET /api/repositories/:name" should {
    "return repository details for a repo name" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/repositories/repo-2")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(fromResource("repositoryDetails.json"))
    }
  }

  "GET /api/repositories" should {
    "return all repositories" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/repositories")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(fromResource("repositories.json"))
    }

    "return only archived repositories" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/repositories?archived=true")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """[
          |  {
          |    "name": "repo-1",
          |    "teamNames": [
          |      "PlatOps"
          |    ],
          |    "createdAt": "2014-03-11T19:59:49Z",
          |    "lastUpdatedAt": "2020-12-03T12:57:55Z",
          |    "repoType": "Service",
          |    "language": "Scala",
          |    "isArchived": true,
          |    "defaultBranch": "main",
          |    "isDeprecated": true
          |  }
          |]""".stripMargin
      )
    }

    "return only non-archived repositories" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/repositories?archived=false")
          .get()
          .futureValue

      response.status shouldBe 200

      val res = response.json.as[Seq[JsObject]]
      res.size shouldBe 6
      res.forall(obj => (obj \ "isArchived").as[Boolean]) shouldBe false
      res.head shouldBe Json.parse(
        """{
          |  "name": "repo-2",
          |  "teamNames": [
          |    "PlatOps"
          |  ],
          |  "createdAt": "2014-03-11T19:59:49Z",
          |  "lastUpdatedAt": "2020-12-03T12:57:55Z",
          |  "repoType": "Service",
          |  "language": "Scala",
          |  "isArchived": false,
          |  "defaultBranch": "main",
          |  "isDeprecated": false
          |}
          |""".stripMargin
      )
    }
  }

  "GET /api/whats-running-where" should {
    "return a list of what's running where data" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/whats-running-where")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(fromResource("whatsRunningWhere.json"))
    }
  }

  "GET /api/whats-running-where/:serviceName" should {
    "return what's running where data for a single service" in {
      val response =
        wsClient
          .url(s"$baseUrl/api/whats-running-where/catalogue-frontend")
          .get()
          .futureValue

      val expectedJson     = Json.parse(fromResource("whatsRunningWhereForService.json"))

      val actualName       = (response.json \ "applicationName").as[String]
      val expectedName     = (expectedJson  \ "applicationName").as[String]

      val actualVersions   = (response.json \ "versions").as[Seq[JsObject]]
      val expectedVersions = (expectedJson  \ "versions").as[Seq[JsObject]]

      response.status shouldBe 200
      actualName      shouldBe expectedName

      actualVersions should contain theSameElementsAs expectedVersions
    }
  }

  private def fromResource(resource: String): String = {
    val resourcePath = s"it/resources/expectedJson/$resource"
    Option(new File(System.getProperty("user.dir"), resourcePath))
      .fold(
        sys.error(s"Could not find resource at $resourcePath")
      )(
        resource =>
          Using(Source.fromFile(resource)) { source =>
            source.mkString
          }.getOrElse {
            sys.error(s"Error reading resource from $resourcePath")
          }
      )
  }
}
