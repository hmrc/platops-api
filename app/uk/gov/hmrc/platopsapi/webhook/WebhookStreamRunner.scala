/*
 * Copyright 2025 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.Done
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.platopsapi.persistence.GithubRequestsQueueRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

@Singleton
class WebhookStreamRunner @Inject()(
  repo            : GithubRequestsQueueRepository
, webhookConnector: WebhookConnector
, config          : Configuration
)(implicit
  ec : ExecutionContext
, mat: Materializer
) extends Logging:

  private val initialDelay: FiniteDuration = config.get[Duration]("webhook-stream.source-tick.initialDelay").toMillis.millis
  private val interval    : FiniteDuration = config.get[Duration]("webhook-stream.source-tick.interval"    ).toMillis.millis

  if   config.get[Boolean]("webhook-stream.enabled")
  then webhookStream(Source.tick(initialDelay = initialDelay, interval = interval, tick = ()))
       logger.info("Started webhook stream")

  def webhookStream(tickSource: Source[Unit, _]): Future[Done] =
    tickSource
      .flatMapConcat: _ =>
        Source.unfoldAsync(()): _ =>
          repo.pullOutstanding
            .map:
              case Some(wi) => Some(((), wi))
              case None     => None
            .recoverWith:
              case ex =>
                logger.error(s"Unable to pull outstanding work item from mongo Reason: ${ex.getMessage}")
                Future.successful(None) // Stream terminates here, hits retry .andThen
      .mapAsync(1): wi =>
        forwardWorkGithubRequest(wi.item)
          .flatMap:_ =>
            repo.completeAndDelete(wi.id)
          .recoverWith:
            case e if wi.failureCount < 3 => logger.error(s"${processingStatusFailedLog(wi)} Reason: ${e.getMessage}")
                                             repo.markAs(wi.id, ProcessingStatus.Failed)
            case e                        => logger.error(s"${processingStatusPermanentlyFailedLog(wi)} Reason: ${e.getMessage}")
                                             repo.markAs(wi.id, ProcessingStatus.PermanentlyFailed)
      .runWith(Sink.ignore)
      .andThen:
        case Failure(ex) => logger.warn(s"Webhook stream failed: ${ex.getMessage} - restarting")
                            webhookStream(tickSource)


  private def forwardWorkGithubRequest(item: GithubWebhookRequest): Future[Unit] =
    logger.info(s"Forwarding github webhook: ${item.xGitHubEventHeader.asString} to ${item.targetUrl}")
    webhookConnector
      .webhook(item.targetUrl, item.payload, item.xGitHubEventHeader)(HeaderCarrier())
      .map(_ => ())

  private def processingStatusFailedLog(wi: WorkItem[GithubWebhookRequest]): String =
    s"Failed to forward webhook due to timeouts - X-Github-Event: ${wi.item.xGitHubEventHeader}, target url: ${wi.item.targetUrl}, retry count: ${wi.failureCount}, retry in: ${config.getMillis("queue.retryAfter") / 1000} seconds"

  private def processingStatusPermanentlyFailedLog(wi: WorkItem[GithubWebhookRequest]): String =
    s"Failed to forward webhook due to timeouts and marked as permanently failed - X-Github-Event: ${wi.item.xGitHubEventHeader}, target url: ${wi.item.targetUrl}, retry count: ${wi.failureCount}"
end WebhookStreamRunner
