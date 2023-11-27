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

import javax.inject.{Inject, Singleton}

@Singleton()
class ApiController @Inject()(
  apiConnector  : ApiConnector,
  servicesConfig: ServicesConfig,
  cc            : ControllerComponents
  ) extends BackendController(cc) {

  private val prCommenterUrl = servicesConfig.baseUrl("pr-commenter")

  private def streamParser: BodyParser[Source[ByteString, _]] = BodyParser { _ =>
    Accumulator.source[ByteString].map(Right.apply)(cc.executionContext)
  }

  def prCommenterBuildhook(repoName: String, prId: Long) =
    Action.async(streamParser) { implicit request =>
      apiConnector.post(url"$prCommenterUrl/pr-commenter/repositories/$repoName/prs/$prId/comments/buildhook", request.body)
    }

  private val teamsAndRepositoriesUrl = servicesConfig.baseUrl("teams-and-repositories")

  def teams() =
    Action.async { implicit request =>
      apiConnector.get(url"$teamsAndRepositoriesUrl/api/v2/teams")
    }
}
