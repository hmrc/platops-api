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

import org.apache.pekko.actor.ActorSystem
import org.scalatest.Assertion
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSClient
import play.api.libs.functional.syntax._
import play.api.libs.json._

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

  "GET /api/v2/decommissioned-services" should {

    case class DecommissionedService(repoName: String)
    implicit val reads: Reads[DecommissionedService] = (__ \ "repoName").read[String].map(DecommissionedService.apply)

    "return a list of decommissioned services" in new Setup {
      post(s"$teamsAndReposBaseUrl/test-only/repos",         fromResource("/it/resources/gitRepositories.json"))
      post(s"$teamsAndReposBaseUrl/test-only/deleted-repos", fromResource("/it/resources/deletedGitRepositories.json"))

      val response = wsClient.url(s"$baseUrl/api/v2/decommissioned-services").get().futureValue
      response.status shouldBe 200

      val parsedList = Json.parse(response.body).as[List[DecommissionedService]]
      parsedList.map(_.repoName) should contain allOf("repo-1", "repo-3", "repo-4")
    }
  }

  trait Setup {
    val wsClient             = app.injector.instanceOf[WSClient]
    val baseUrl              = s"http://localhost:$port"
    val teamsAndReposBaseUrl = s"http://localhost:9015"

    private def is2xx(status: Int) = status >= 200 && status < 300

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


    def fromResource(resourcePath: String): String =
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

