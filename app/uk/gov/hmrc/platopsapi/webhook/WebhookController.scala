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

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import cats.implicits._
import play.api.{Configuration, Logging}
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, BodyParser}
import play.api.libs.streams.Accumulator

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

  private val leakDetectionWebhookSecretKey = config.get[String]("github.leakDetectionWebhookSecretKey")
  private val prCommenterWebhookSecretKey   = config.get[String]("github.prCommenterWebhookSecretKey")
  private val platopApiWebhookSecretKey     = config.get[String]("github.webhookSecretKey")
  import uk.gov.hmrc.http.StringContextOps
  private val leakDetectionUrl  = url"${servicesConfig.baseUrl("leak-detection")}/leak-detection/validate"
  private val prCommenterUrl    = url"${servicesConfig.baseUrl("pr-commenter")}/pr-commenter/webhook"
  private val serviceConfigsUrl = url"${servicesConfig.baseUrl("service-configs")}/service-configs/webhook"

  def processGithubWebhook(keyType: Option[String] = None) =
    Action.async(
      BodyParser { rh =>
        val githubWebhookSecretKey = keyType match {
          case Some("leak-detection") => leakDetectionWebhookSecretKey
          case Some("pr-commenter")   => prCommenterWebhookSecretKey
          case _                      => platopApiWebhookSecretKey
        }
        Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _))
          .map(_.utf8String)
          .map { payloadAsString =>
            rh.headers.get("X-Hub-Signature-256") match {
              case Some(sig) if WebhookController.isSignatureValid(payloadAsString, githubWebhookSecretKey, sig)
                           => Right(payloadAsString)
              case Some(_) => Left(BadRequest(details("Invalid Signature")))
              case None    => Left(BadRequest(details("Signature not found in headers")))
            }
          }
      }
    ) { implicit request =>
      Json
        .parse(request.body)
        .asOpt[GithubRequest](GithubRequest.reads) match {
          case Some(pull: GithubRequest.PullRequest) =>
            logger.info(s"Repo: ${pull.repoName} Pull: ${pull.id} - received")
            webhookConnector
              .webhook(prCommenterUrl, request.body)
              .map {
                case rsp if rsp.header.status == 200 => Ok(details("Pull request processed"))
                case rsp                             => rsp
              }
          case Some(push: GithubRequest.Push) =>
            logger.info(s"Repo: ${push.repoName} Push received")
            ( webhookConnector.webhook(leakDetectionUrl, request.body)
            , webhookConnector.webhook(serviceConfigsUrl, request.body)
            ).mapN( (rsp1, rsp2) =>
              if      (rsp1.header.status != 200) rsp1
              else if (rsp2.header.status != 200) rsp2
              else                                Ok(details("Push request processed"))
            )
          case Some(_: GithubRequest.Ping) =>
            logger.info(s"Ping request received")
            Future.successful(Ok(details("Ping request processed")))
          case None =>
            logger.info(s"Bad request ${request.body}")
            Future.successful(BadRequest(details("Invalid payload")))
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
