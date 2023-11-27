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

package uk.gov.hmrc.platopsapi.webhook

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import cats.implicits._
import play.api.{Configuration, Logging}
import play.api.http.{Status => HttpStatus}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.{ControllerComponents, BodyParser}

import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class WebhookController @Inject()(
  config          : Configuration,
  servicesConfig  : ServicesConfig,
  webhookConnector: WebhookConnector,
  cc: ControllerComponents
)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  private val platopApiWebhookSecretKey     = config.get[String]("github.webhookSecretKey")
  import uk.gov.hmrc.http.StringContextOps
  private val internalAuthUrl   = url"${servicesConfig.baseUrl("internal-auth")}/internal-auth/webhook"
  private val leakDetectionUrl  = url"${servicesConfig.baseUrl("leak-detection")}/leak-detection/validate"
  private val prCommenterUrl    = url"${servicesConfig.baseUrl("pr-commenter")}/pr-commenter/webhook"
  private val serviceConfigsUrl = url"${servicesConfig.baseUrl("service-configs")}/service-configs/webhook"
  private val teamsAndReposUrl  = url"${servicesConfig.baseUrl("teams-and-repositories")}/teams-and-repositories/webhook"

  private val eventMap: Map[WebhookEvent, List[java.net.URL]] = Map(
    WebhookEvent.Pull       -> List(prCommenterUrl)
  , WebhookEvent.Push       -> List(leakDetectionUrl, serviceConfigsUrl, teamsAndReposUrl)
  , WebhookEvent.Repository -> List(leakDetectionUrl)
  , WebhookEvent.Team       -> List(internalAuthUrl, teamsAndReposUrl)
  , WebhookEvent.Ping       -> Nil
  )

  def processGithubWebhook() =
    Action.async(
      BodyParser { rh =>
        Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _))
          .map(_.utf8String)
          .map { payloadAsString =>
            rh.headers.get("X-Hub-Signature-256") match {
              case Some(sig) if WebhookController.isSignatureValid(payloadAsString, platopApiWebhookSecretKey, sig)
                           => Right(payloadAsString)
              case Some(_) => Left(BadRequest(details("Invalid Signature")))
              case None    => Left(BadRequest(details("Signature not found in headers")))
            }
          }
      }
    ) { implicit request =>
      request
        .headers
        .get("X-GitHub-Event")
        .map(WebhookEvent.parse) match {
          case Some(Right(event)) => logger.info(s"Forwarding webhook ${event.asString} to ${eventMap(event).mkString(" and ")}")
                                     eventMap(event)
                                      .traverse(url => webhookConnector.webhook(url, request.body))
                                      .map(_.find(x => !HttpStatus.isSuccessful(x.header.status)))
                                      .map(_.getOrElse(Ok(details(s"Event type '${event.asString}' processing"))))
          case Some(Left(other))  => logger.warn(s"Bad request X-GitHub-Event not supported $other")
                                     Future.successful(BadRequest(details("Invalid event type")))
          case None               => logger.warn(s"Bad request X-GitHub-Event not specified")
                                     Future.successful(BadRequest(details("Event type not specified")))
      }
    }

  private def details(msg: String) =
    Json.obj("details" -> msg)
}

object WebhookController {
  import org.apache.commons.codec.digest.HmacAlgorithms
  import javax.crypto.Mac
  import javax.crypto.spec.SecretKeySpec
  import javax.xml.bind.DatatypeConverter

  def isSignatureValid(payload: String, secret: String, ghSignature: String): Boolean = {
    val algorithm  = HmacAlgorithms.HMAC_SHA_256.toString
    val secretSpec = new SecretKeySpec(secret.getBytes(), algorithm)
    val hmac       = Mac.getInstance(algorithm)

    hmac.init(secretSpec)

    val sig           = hmac.doFinal(payload.getBytes("UTF-8"))
    val hashOfPayload = s"sha256=${DatatypeConverter.printHexBinary(sig)}"

    ghSignature.equalsIgnoreCase(hashOfPayload)
  }
}
