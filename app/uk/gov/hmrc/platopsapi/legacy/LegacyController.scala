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

package uk.gov.hmrc.platopsapi.legacy

import play.api.Logging
import play.api.mvc.ControllerComponents
import play.api.libs.ws.writeableOf_JsValue
import play.api.libs.json._
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.platopsapi.api.{ApiConnector, routes}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class LegacyController @Inject()(
  apiConnector  : ApiConnector
, servicesConfig: ServicesConfig
, httpClientV2  : HttpClientV2
, cc            : ControllerComponents
)(using
  ec: ExecutionContext
) extends BackendController(cc) with Logging:

  private val slackNotificationsUrl   = servicesConfig.baseUrl("slack-notifications")
  private val teamsAndRepositoriesUrl = servicesConfig.baseUrl("teams-and-repositories")

  def redirectWhatsRunningWhere() =
    Action.async:
      Future(Redirect(routes.ApiController.whatsRunningWhere(), MOVED_PERMANENTLY))

  def redirectWhatsRunningWhereForService(serviceName: String) =
    Action.async:
      Future(Redirect(routes.ApiController.whatsRunningWhereForService(serviceName), MOVED_PERMANENTLY))

  def sendLegacySlackNotification() =
    Action.async(parse.json):
      implicit request =>
        apiConnector.post(url"$slackNotificationsUrl/slack-notifications/notification", request.body)

  def sendSlackNotification() =
    Action.async(parse.json):
      implicit request =>
        apiConnector.post(url"$slackNotificationsUrl/slack-notifications/v2/notification", request.body)

  def slackNotificationStatus(msgId: String) =
    Action.async:
      implicit request =>
        apiConnector.get(url"$slackNotificationsUrl/slack-notifications/v2/$msgId/status")

  import uk.gov.hmrc.http.HttpReads.Implicits._
  def teamsWithRepos() =
    Action.async:
      implicit request =>
        given Reads[LegacyController.GitRepository] = LegacyController.GitRepository.reads
        for
          repos <- httpClientV2
                      .get(url"$teamsAndRepositoriesUrl/api/v2/repositories")
                      .execute[Seq[LegacyController.GitRepository]]
          json  =  repos
                      .flatMap(x => x.teamNames.map(t => (t, x)))
                      .groupBy(_._1)
                      .map: (teamName, xs) =>
                        val missing =
                          LegacyController.RepoType.values.map(_.asString -> JsArray.empty)

                        val found =
                          xs.map(_._2)
                            .groupBy(_.repoType)
                            .map: (repoType, ys) =>
                              repoType -> JsArray(ys.sortBy(_.name).map(y => JsString(y.name)))
                            .toSeq

                        JsObject(Seq(
                          "name"           -> JsString(teamName)
                        , "createdDate"    -> JsString(xs.map(_._2.createdDate).max.toString)
                        , "lastActiveDate" -> JsString(xs.map(_._2.lastActiveDate).max.toString)
                        , "repos"          -> JsObject(missing).deepMerge(JsObject(found))
                        , "ownedRepos"     -> JsArray:
                                                xs.map(_._2.name)
                                                  .sorted
                                                  .map(JsString.apply)
                        ))
        yield Ok(JsArray(json.toSeq))

  def repositories(archived: Option[Boolean]) =
    Action.async:
      implicit request =>
        httpClientV2
          .get(url"$teamsAndRepositoriesUrl/api/v2/repositories?archived=$archived")
          .execute[JsArray]
          .map:
            _.value.flatMap(json => toLegacy(displayMulti = true, json))
          .map: xs =>
            Ok.apply(JsArray.apply(xs))

  def repositoryDetails(name: String) =
    Action.async:
      implicit request =>
        httpClientV2
          .get(url"""$teamsAndRepositoriesUrl/api/v2/repositories?name="$name"""") // note single quote + escaping would encode the backslash
          .execute[JsArray]
          .map:
            _.value.headOption.flatMap(json => toLegacy(displayMulti = false, json))
          .map:
            case Some(json) => Ok.apply(json)
            case None       => NotFound

  private def toLegacy(displayMulti: Boolean, json: JsValue): Option[JsValue] =
    import Reads.JsObjectReducer
    json.transform(
      if   displayMulti
      then LegacyController.GitRepository.jsonLegacyMultiTransformer.reduce
      else LegacyController.GitRepository.jsonLegacySingleTransformer.reduce
    ) match
      case JsSuccess(json, _) => Some(json)
      case JsError(errors   ) => logger.error(s"Unexpected error formatting json: $errors")
                                 None

object LegacyController:
  import play.api.libs.functional.syntax._
  import java.time.Instant

  enum RepoType(val asString: String):
    case Service   extends RepoType("Service"  )
    case Library   extends RepoType("Library"  )
    case Prototype extends RepoType("Prototype")
    case Test      extends RepoType("Test"     )
    case Other     extends RepoType("Other"    )

  case class GitRepository(
    name          : String,
    createdDate   : Instant,
    lastActiveDate: Instant,
    repoType      : String,
    teamNames     : List[String] = Nil,
  )

  object GitRepository:
    val reads: Reads[GitRepository] =
      ( (__ \ "name"          ).read[String]
      ~ (__ \ "createdDate"   ).read[Instant]
      ~ (__ \ "lastActiveDate").read[Instant]
      ~ (__ \ "repoType"      ).read[String]
      ~ (__ \ "teamNames"     ).readWithDefault[List[String]](Nil)
      )(GitRepository.apply _)

    val jsonLegacyMultiTransformer =
      ( (__ \ "name"              ).json.copyFrom((__ \ "name"          ).json.pick)
      ~ (__ \ "createdAt"         ).json.copyFrom((__ \ "createdDate"   ).json.pick)
      ~ (__ \ "lastUpdatedAt"     ).json.copyFrom((__ \ "lastActiveDate").json.pick)
      ~ (__ \ "repoType"          ).json.copyFrom((__ \ "repoType"      ).json.pick)
      ~ (__ \ "teamNames"         ).json.copyFrom((__ \ "teamNames"     ).json.pick)
      ~ (__ \ "language"          ).json.copyFrom((__ \ "language"      ).json.pick).orElse(Reads.pure(Json.obj("language" -> JsNull)))
      ~ (__ \ "defaultBranch"     ).json.copyFrom((__ \ "defaultBranch" ).json.pick)
      ~ (__ \ "isArchived"        ).json.copyFrom((__ \ "isArchived"    ).json.pick)
      ~ (__ \ "isDeprecated"      ).json.copyFrom((__ \ "isDeprecated"  ).json.pick)
      )

    val jsonLegacySingleTransformer =
      ( (__ \ "name"              ).json.copyFrom((__ \ "name"          ).json.pick)
      ~ (__ \ "description"       ).json.copyFrom((__ \ "description"   ).json.pick)
      ~ (__ \ "createdAt"         ).json.copyFrom((__ \ "createdDate"   ).json.pick)
      ~ (__ \ "lastActive"        ).json.copyFrom((__ \ "lastActiveDate").json.pick)
      ~ (__ \ "repoType"          ).json.copyFrom((__ \ "repoType"      ).json.pick)
      ~ (__ \ "teamNames"         ).json.copyFrom((__ \ "teamNames"     ).json.pick)
      ~ (__ \ "owningTeams"       ).json.copyFrom((__ \ "owningTeams"   ).json.pick)
      ~ (__ \ "language"          ).json.copyFrom((__ \ "language"      ).json.pick).orElse(Reads.pure(Json.obj("language" -> JsNull)))
      ~ (__ \ "defaultBranch"     ).json.copyFrom((__ \ "defaultBranch" ).json.pick)
      ~ (__ \ "isArchived"        ).json.copyFrom((__ \ "isArchived"    ).json.pick)
      ~ (__ \ "isPrivate"         ).json.copyFrom((__ \ "isPrivate"     ).json.pick)
      ~ (__ \ "isDeprecated"      ).json.copyFrom((__ \ "isDeprecated"  ).json.pick)
      ~ (__ \ "githubUrl" \ "url" ).json.copyFrom((__ \ "url"           ).json.pick)
      ~ (__ \ "githubUrl"         ).json.put(Json.obj("name" -> "github-com", "displayName" -> "GitHub.com"))
      )
