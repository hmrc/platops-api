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

package uk.gov.hmrc.platopsapi.stub

import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.MongoClient
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Json, Reads, __}
import play.api.libs.ws.{WSClient, WSClientConfig}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import uk.gov.hmrc.platopsapi.ResourceUtil
import uk.gov.hmrc.platopsapi.ResourceUtil.fromResource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object TestStubs {
  private val teamsAndRepositoriesBaseUrl = "http://localhost:9015"
  private val releasesApiBaseUrl          = "http://localhost:8008"
  private val internalAuthBaseUrl         = "http://localhost:8470"
  private val dbNames                     = Set("teams-and-repositories", "releases", "internal-auth") // add database name here to check for production data

  private val gitRepositories        = s"$teamsAndRepositoriesBaseUrl/test-only/repos"
  private val deletedGitRepositories = s"$teamsAndRepositoriesBaseUrl/test-only/deleted-repos"
  private val teamSummaries          = s"$teamsAndRepositoriesBaseUrl/test-only/team-summaries"
  private val releaseEvents          = s"$releasesApiBaseUrl/test-only/release-events"
  private val internalAuthToken      = s"$internalAuthBaseUrl/test-only/token"

  private val wsClient: WSClient = {
    implicit val as: ActorSystem = ActorSystem("test-actor-system")
    AhcWSClient(AhcWSClientConfig(
      wsClientConfig = WSClientConfig(
        connectionTimeout = 10.seconds,
        requestTimeout    = 1.minute
      )
    ))
  }

  private def is2xx(status: Int) = status >= 200 && status < 300

  private def put(url: String, payload: String): Future[Unit] =
    wsClient
      .url(url)
      .withHttpHeaders("content-type" -> "application/json")
      .put(payload)
      .map { response => assert(is2xx(response.status), s"Failed to call stub PUT $url: ${response.body}"); () }
      .recoverWith { case e => Future.failed(new RuntimeException(s"Failed to call stub POST $url: ${e.getMessage}", e)) }

  //TODO replace with PUT
  private def post(url: String, payload: String): Future[Unit] =
    wsClient
      .url(url)
      .withHttpHeaders("content-type" -> "application/json")
      .post(payload)
      .map { response => assert(is2xx(response.status), s"Failed to call stub POST $url: ${response.body}"); () }
      .recoverWith { case e => Future.failed(new RuntimeException(s"Failed to call stub POST $url: ${e.getMessage}", e)) }

  //TODO replace with PUT
  private def delete(url: String): Future[Unit] =
    wsClient
      .url(url)
      .delete()
      .map { response => assert(is2xx(response.status), s"Failed to call stub DELETE $url: ${response.body}"); () }
      .recoverWith { case e => Future.failed(new RuntimeException(s"Failed to call stub DELETE $url: ${e.getMessage}", e)) }

  private def resetServices(): Future[List[Unit]] =
    Future.sequence(
      List(
        //teams-and-repositories
        delete(gitRepositories).flatMap(_ => post(gitRepositories, fromResource("/seedCollectionsJson/gitRepositories.json"))),
        put(deletedGitRepositories, fromResource("/seedCollectionsJson/deletedGitRepositories.json")),
        delete(teamSummaries).flatMap(_ => post(teamSummaries, fromResource("/seedCollectionsJson/teamSummaries.json"))),
        //releases-api
        delete(releaseEvents).flatMap(_ => post(releaseEvents, fromResource("/seedCollectionsJson/deploymentEvents.json"))),
        //slack-notifications
        post(internalAuthToken, fromResource("/seedCollectionsJson/slackNotificationsToken.json")),
        post(internalAuthToken, fromResource("/seedCollectionsJson/slackNotificationsTokenNoPermissions.json"))
      )
    )

  case class Database(name: String, sizeOnDisk: Long)

  object Database {
    implicit val reads: Reads[Database] =
      ( (__ \ "name"      ).read[String]
      ~ (__ \ "sizeOnDisk").read[Long]
      )(Database.apply _)
  }

  private def checkMongoProdData(): Future[Unit] = {
    val dataThreshold = 1000000 // bytes
    val dataThresholdAsMb = dataThreshold / 1e+6

    MongoClient()
      .listDatabases()
      .toFuture()
      .map {
        _.map(document => Json.parse(document.toJson()).as[Database](Database.reads))
          .filter(db => dbNames.contains(db.name))
          .filter(db => db.sizeOnDisk > dataThreshold && db.name != "local")
      }.map {
        case dbs if dbs.isEmpty => ()
        case dbs => throw new Exception(
          s"The following databases were detected to have real data (more than $dataThresholdAsMb mb): " +
            s"${dbs.map(_.name).mkString(", ")}. Ensure you run the local mongo setup script to start an ephemeral mongo instance."
        )
      }
  }

  def resetAll(): Unit =
    Await.result(
      for {
        _ <- checkMongoProdData()
        _ <- resetServices()
      } yield ()
      , 2.minutes
    )
}
