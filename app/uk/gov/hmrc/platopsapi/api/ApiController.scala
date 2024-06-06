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

import org.apache.pekko.util.ByteString
import org.apache.pekko.stream.scaladsl.Source
import play.api.mvc.{BodyParser, ControllerComponents}
import play.api.libs.streams.Accumulator
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton()
class ApiController @Inject()(
  apiConnector  : ApiConnector,
  servicesConfig: ServicesConfig,
  cc            : ControllerComponents
) extends BackendController(cc) {

  private val prCommenterUrl           = servicesConfig.baseUrl("pr-commenter")
  private val teamsAndRepositoriesUrl  = servicesConfig.baseUrl("teams-and-repositories")
  private val releasesApiUrl           = servicesConfig.baseUrl("releases-api")
  private val slackNotificationsUrl    = servicesConfig.baseUrl("slack-notifications")

  private def streamParser: BodyParser[Source[ByteString, _]] = BodyParser { _ =>
    Accumulator.source[ByteString].map(Right.apply)(cc.executionContext)
  }

  def prCommenterBuildhook(repoName: String, prId: Long) =
    Action.async(streamParser) { implicit request =>
      apiConnector.post(url"$prCommenterUrl/pr-commenter/repositories/$repoName/prs/$prId/comments/buildhook", request.body)
    }

  def decommissionedRepos(repoType: Option[String] = None) =
    Action.async { implicit request =>
      apiConnector.get(url"$teamsAndRepositoriesUrl/api/v2/decommissioned-repositories?repoType=$repoType")
    }

  def repositoriesV2(
    name       : Option[String],
    team       : Option[String],
    owningTeam : Option[String],
    archived   : Option[Boolean],
    repoType   : Option[String],
    serviceType: Option[String],
    tag        : Option[List[String]]
  ) = Action.async { implicit request =>

    val queryParams = Seq(
      name.map("name" -> _)
    , team.map("team" -> _)
    , owningTeam.map("owningTeam" -> _)
    , archived.map("archived" -> _.toString)
    , repoType.map("repoType" -> _)
    , serviceType.map("serviceType" -> _)
    ).flatten ++
      tag.getOrElse(Seq.empty).map("tag" -> _)

    apiConnector.get(url"$teamsAndRepositoriesUrl/api/v2/repositories?$queryParams")
  }

  def teams() =
    Action.async { implicit request =>
      apiConnector.get(url"$teamsAndRepositoriesUrl/api/v2/teams")
    }

  def teamsWithRepos() =
    Action.async { implicit request =>
      apiConnector.get(url"$teamsAndRepositoriesUrl/api/teams_with_repositories")
    }

  def repositoryDetails(name: String) =
    Action.async { implicit  request =>
      apiConnector.get(url"$teamsAndRepositoriesUrl/api/repositories/$name")
    }

  def repositories(archived: Option[Boolean]) =
    Action.async { implicit  request =>
      apiConnector.get(url"$teamsAndRepositoriesUrl/api/repositories?archived=$archived")
    }

  def whatsRunningWhere() =
    Action.async { implicit request =>
      apiConnector.get(url"$releasesApiUrl/releases-api/whats-running-where")
    }

  def whatsRunningWhereForService(serviceName: String) =
    Action.async { implicit request =>
      apiConnector.get(url"$releasesApiUrl/releases-api/whats-running-where/$serviceName")
    }

  def sendLegacySlackNotification() =
    Action.async(parse.json) { implicit  request =>
      apiConnector.post(url"$slackNotificationsUrl/slack-notifications/notification", request.body)
    }

  def sendSlackNotification() =
    Action.async(parse.json) { implicit  request =>
      apiConnector.post(url"$slackNotificationsUrl/slack-notifications/v2/notification", request.body)
    }

  def status(msgId: String) =
    Action.async { implicit request =>
      apiConnector.get(url"$slackNotificationsUrl/slack-notifications/v2/$msgId/status")
    }
}
