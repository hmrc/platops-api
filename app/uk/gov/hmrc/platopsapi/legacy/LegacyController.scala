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
import play.api.libs.json.*
import play.api.libs.ws.writeableOf_JsValue
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.platopsapi.api.{ApiConnector, routes}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class LegacyController @Inject()(
  apiConnector  : ApiConnector
, servicesConfig: ServicesConfig
, cc            : ControllerComponents
)(using
  ec: ExecutionContext
) extends BackendController(cc) with Logging:

  private val slackNotificationsUrl   = servicesConfig.baseUrl("slack-notifications")

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
