/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.platopsapi.webhook

import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class WebhookController @Inject()(githubWebhookProxy: GithubWebhookProxy,
                                  servicesConfig: ServicesConfig,
                                  cc: ControllerComponents)
  extends BackendController(cc) {

  lazy private val leakDetectionUrl: URL = url"${servicesConfig.baseUrl("leak-detection")}/leak-detection/validate"
  lazy private val prCommenterUrl: URL = url"${servicesConfig.baseUrl("pr-commenter")}/pr-commenter/webhook"

  def leakDetection(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    proxyRequestTo(leakDetectionUrl, request)
  }

  def prCommenter(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    proxyRequestTo(prCommenterUrl, request)
  }

  private def proxyRequestTo(url: URL, request: Request[JsValue])(implicit hc: HeaderCarrier): Future[Result] = {
    githubWebhookProxy
      .webhook(url, request.body)
  }

}
