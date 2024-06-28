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

import cats.Applicative
import cats.implicits.*
import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.MongoClient
import org.mongodb.scala.ObservableFuture
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.ws.writeableOf_JsValue
import play.api.libs.json.{JsArray, JsNull, JsObject, JsResult, JsSuccess, JsValue, Json, Reads, __}
import play.api.libs.ws.{WSClient, WSClientConfig}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import uk.gov.hmrc.platopsapi.ResourceUtil.fromResource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object TestStubs {
  private val teamsAndRepositoriesBaseUrl = "http://localhost:9015"
  private val releasesApiBaseUrl          = "http://localhost:8008"
  private val internalAuthBaseUrl         = "http://localhost:8470"
  private val serviceConfigsBaseUrl       = "http://localhost:8460"
  private val serviceDependenciesBaseUrl  = "http://localhost:8459"
  private val dbNames                     = Set("teams-and-repositories", "releases", "internal-auth", "service-configs", "service-dependencies") // add database name here to check for production data

  private val gitRepositories            = s"$teamsAndRepositoriesBaseUrl/test-only/repos"
  private val deletedGitRepositories     = s"$teamsAndRepositoriesBaseUrl/test-only/deleted-repos"
  private val teamSummaries              = s"$teamsAndRepositoriesBaseUrl/test-only/team-summaries"
  private val releaseEvents              = s"$releasesApiBaseUrl/test-only/release-events"
  private val internalAuthToken          = s"$internalAuthBaseUrl/test-only/token"
  private val bobbyRules                 = s"$serviceConfigsBaseUrl/test-only/bobbyRules"
  private val dependenciesLatestVersions = s"$serviceDependenciesBaseUrl/test-only/latestVersions"
  private val metaArtefacts              = s"$serviceDependenciesBaseUrl/test-only/meta-artefacts"

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
      .put(Json.parse(payload))
      .map { response => assert(is2xx(response.status), s"Failed to call stub PUT $url: ${response.body}"); () }
      .recoverWith { case e => Future.failed(new RuntimeException(s"Failed to call stub POST $url: ${e.getMessage}", e)) }

  //TODO replace with PUT
  private def post(url: String, payload: String): Future[Unit] =
    wsClient
      .url(url)
      .post(Json.parse(payload))
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
        post(internalAuthToken, fromResource("/seedCollectionsJson/slackNotificationsTokenNoPermissions.json")),
        //service-configs
        delete(bobbyRules).flatMap(_ => post(bobbyRules, fromResource("/seedCollectionsJson/defaultBobbyRules.json"))),
        //service-dependencies
        delete(dependenciesLatestVersions).flatMap(_ => post(dependenciesLatestVersions, fromResource("/seedCollectionsJson/defaultLatestVersions.json"))),
        delete(metaArtefacts).flatMap(_ => post(metaArtefacts, transformMetaArtefacts(fromResource("/seedCollectionsJson/defaultMetaArtefacts.json"))))
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

  // for meta-artefact json, it is easier to maintain an array of dependencies and convert to dependencyGraph
  private def transformMetaArtefacts(json: String): String =
    JsArray(
      Json.parse(json)
        .as[JsArray]
        .value
        .map(_.transform(transformMetaArtefact).fold[JsValue](err => sys.error(s"Invalid metaArtefactJson: $err"), identity))
    ).toString


  private def transformMetaArtefact: Reads[JsValue] =
    (metaArtefactJson: JsValue) =>
      for {
        slugInfoJsObj <- (metaArtefactJson                      ).validate[JsObject]
        repoName      <- (metaArtefactJson \ "name"             ).validate[String]
        repoVersion   <- (metaArtefactJson \ "version"          ).validate[String]
        build         <- (metaArtefactJson \ "dependenciesBuild").validate[List[JsObject]]
        modules       <- (metaArtefactJson \ "modules"          ).validate[List[JsObject]]
        modules2      <- modules.traverse(transformMetaArtefactModule(repoVersion))
      } yield
        slugInfoJsObj ++
          Json.obj(
            "dependenciesBuild" -> JsNull,
            "dependencyDotBuild" -> generateDependencyGraph(repoName, repoVersion, build),
            "modules"            -> modules2
          )

  private def transformMetaArtefactModule(repoVersion: String)(moduleJson: JsValue): JsResult[JsValue] =
    for {
      moduleJsObj <- (moduleJson                         ).validate[JsObject]
      moduleName  <- (moduleJson \ "name"                ).validate[String]
      compile     <- (moduleJson \ "dependenciesCompile" ).validate[List[JsObject]]
      provided    <- (moduleJson \ "dependenciesProvided").validate[List[JsObject]]
      test        <- (moduleJson \ "dependenciesTest"    ).validate[List[JsObject]]
      it          <- (moduleJson \ "dependenciesIt"      ).validate[List[JsObject]]
    } yield
      moduleJsObj ++
        Json.obj(
          "dependenciesCompile"   -> JsNull,
          "dependencyDotCompile"  -> generateDependencyGraph(moduleName, repoVersion, compile),
          "dependencyDotProvided" -> JsNull,
          "dependenciesTest"      -> JsNull,
          "dependencyDotTest"     -> generateDependencyGraph(moduleName, repoVersion, test),
          "dependencyDotIt"       -> generateDependencyGraph(moduleName, repoVersion, it)
        )

  private def generateDependencyGraph(rootName: String, rootVersion: String, dependencies: List[JsObject]): String =
    s"""digraph "dependency-graph" {
       |  graph[rankdir="LR"]
       |  edge [
       |    arrowtail="none"
       |  ]
       |  "uk.gov.hmrc:${rootName}:${rootVersion}"[label=<uk.gov.hmrc<BR/><B>${rootName}</B><BR/>${rootVersion}> style=""]
       |${dependencies.map{dj =>
           val dependencyGroup   = (dj \ "group"   ).as[String]
           val dependencyName    = (dj \ "artifact").as[String]
           val dependencyVersion = (dj \ "version" ).as[String]
           s"""  "${dependencyGroup}:${dependencyName}:${dependencyVersion}"[label=<${dependencyGroup}<BR/><B>${dependencyName}</B><BR/>${dependencyVersion}> style=""]"""
         }.mkString("\n")}
       |}""".stripMargin

  implicit val applicativeJsResult: Applicative[JsResult] =
    new Applicative[JsResult] {
      override def pure[A](a: A): JsResult[A] =
        JsSuccess(a)
      override def ap[A, B](ff: JsResult[A => B])(fa: JsResult[A]): JsResult[B] =
        for {
          f <- ff
          a <- fa
        } yield f(a)
    }
}
