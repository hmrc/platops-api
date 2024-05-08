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

import org.scalatest.Assertion
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSClient
import play.api.libs.json._
import uk.gov.hmrc.platopsapi.models.RepoType

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.Using

class ApiControllerISpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneServerPerSuite {

  "GET /api/v2/decommissioned-repositories" should {
    "return a list of decommissioned repositories" in new Setup {
      for {
        _ <- delete(s"$teamsAndReposBaseUrl/test-only/repos")
        _ <- post(s"$teamsAndReposBaseUrl/test-only/repos", fromResource("gitRepositories.json"))
        _ <- put(s"$teamsAndReposBaseUrl/test-only/deleted-repos", fromResource("deletedGitRepositories.json"))
      } yield ()

      val response =
        wsClient
          .url(s"$baseUrl/api/decommissioned-repositories")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """[{"repoName":"repo-1", "repoType": "Service"},{"repoName":"repo-3", "repoType": "Service"},{"repoName":"repo-4","repoType": "Library"},{"repoName":"repo-5","repoType": "Other"}]"""
      )
    }

    "return a list of decommissioned services" in new Setup {
      for {
        _ <- delete(s"$teamsAndReposBaseUrl/test-only/repos")
        _ <- post(s"$teamsAndReposBaseUrl/test-only/repos", fromResource("gitRepositories.json"))
        _ <- put(s"$teamsAndReposBaseUrl/test-only/deleted-repos", fromResource("deletedGitRepositories.json"))
      } yield ()

      val response =
        wsClient
          .url(s"$baseUrl/api/decommissioned-repositories?repoType=${RepoType.Service}")
          .get()
          .futureValue

      response.status shouldBe 200
      response.json   shouldBe Json.parse(
        """[{"repoName":"repo-1", "repoType": "Service"},{"repoName":"repo-3", "repoType": "Service"}]"""
      )
    }
  }

  trait Setup {
    val wsClient             = app.injector.instanceOf[WSClient]
    val baseUrl              = s"http://localhost:$port"
    val teamsAndReposBaseUrl = s"http://localhost:9015"

    private def is2xx(status: Int) = status >= 200 && status < 300

    def put(url: String, payload: String): Future[Unit] =
      wsClient
        .url(url)
        .withHttpHeaders("content-type" -> "application/json")
        .put(payload)
        .map { response =>
          assert(is2xx(response.status), s"Failed to call stub PUT $url: ${response.body}")
        }

    def post(url: String, payload: String): Future[Assertion] =
      wsClient
        .url(url)
        .withHttpHeaders("content-type" -> "application/json")
        .post(payload)
        .map { response =>
          assert(is2xx(response.status), s"Failed to call stub POST $url: ${response.body}")
        }.recoverWith { case e =>
          Future.failed(new RuntimeException(s"Failed to call stub POST $url: ${e.getMessage}", e))
        }

    def delete(url: String): Future[Assertion] =
      wsClient
        .url(url)
        .delete()
        .map { response =>
          assert(is2xx(response.status), s"Failed to call stub DELETE $url: ${response.body}")
        }.recoverWith { case e =>
          Future.failed(new RuntimeException(s"Failed to call stub DELETE $url: ${e.getMessage}", e))
        }

    def fromResource(resource: String): String = {
      val resourcePath = s"it/resources/$resource"
      Option(new File(System.getProperty("user.dir"), resourcePath))
        .fold(
          sys.error(s"Could not find resource at $resourcePath")
        )(resource =>
          Using(Source.fromFile(resource)) { source =>
            source.mkString
          }.getOrElse {
            sys.error(s"Error reading resource from $resourcePath")
          }
        )
    }
  }
}
