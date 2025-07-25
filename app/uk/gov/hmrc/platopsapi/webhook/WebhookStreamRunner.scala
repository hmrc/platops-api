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
import org.apache.pekko.{Done, NotUsed}
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.platopsapi.persistence.GithubRequestsQueueRepository
import uk.gov.hmrc.platopsapi.webhook.{GithubWebhookRequest, WebhookConnector}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebhookStreamRunner @Inject()(
  repo            : GithubRequestsQueueRepository
, webhookConnector: WebhookConnector
)(implicit
  ec : ExecutionContext
, mat: Materializer
) extends Logging:

  private def processingStatusFailedLog(wi: WorkItem[GithubWebhookRequest]): String =
    s"Failed to forward webhook due to timeouts - X-Github-Event: ${wi.item.xGitHubEventHeader}, target url: ${wi.item.targetUrl}, retry count: ${wi.failureCount}, timeoutMillis:"

  private def processingStatusPermanentlyFailedLog(wi: WorkItem[GithubWebhookRequest]): String =
    s"Failed to forward webhook due to timeouts and marked as permanently failed - X-Github-Event: ${wi.item.xGitHubEventHeader}, target url: ${wi.item.targetUrl}, retry count: ${wi.failureCount}, timeoutMillis: "

  private def forwardWorkGithubRequest(item: GithubWebhookRequest): Future[Unit] =
    logger.info(s"Forwarding github webhook: ${item.xGitHubEventHeader.asString} to ${item.targetUrl}")
    webhookConnector
      .webhook(item.targetUrl, item.payload, item.xGitHubEventHeader)(HeaderCarrier())
      .flatMap { r =>
        val fakeStatus = 500 // or 400
        logger.error(s"Overriding response: faking failure with status $fakeStatus for ${item.xGitHubEventHeader}")
        Future.failed(new RuntimeException(s"Simulated $fakeStatus failure"))
      }

  private def pullWorkItem: Future[Option[(Unit, WorkItem[GithubWebhookRequest])]] =
    repo.pullOutstanding
      .map:
        case Some(wi) => Some(((), wi))
        case None     => None
      .recoverWith:
        case ex =>
          logger.error(s"Error: unable to pull outstanding work item from mongo", ex)
          Future.successful(None) // Stream fails here, hits retry .andThen

  private def processWorkItem(wi: WorkItem[GithubWebhookRequest]): Future[Boolean] =
    forwardWorkGithubRequest(wi.item)
      .flatMap(_ => repo.completeAndDelete(wi.id))
      .recoverWith:
        case e if wi.failureCount < 3 => logger.error(s"${processingStatusFailedLog(wi)} Reason: ${e.getMessage}")
                                         repo.markAs(wi.id, ProcessingStatus.Failed)
        case e                        => logger.error(s"${processingStatusPermanentlyFailedLog(wi)} Reason: ${e.getMessage}")
                                         repo.markAs(wi.id, ProcessingStatus.PermanentlyFailed)

  private def streamToWebhook: Future[Done] =
    Source.tick(initialDelay = FiniteDuration(0, SECONDS), interval = FiniteDuration(5, SECONDS), tick = NotUsed)
      .flatMapConcat(_ => Source.unfoldAsync(())(_ => pullWorkItem))
      .mapAsync(1)(processWorkItem)
      .runWith(Sink.ignore)
      .andThen: res =>
        logger.info(s"Webhook stream terminated: $res - restarting")
        streamToWebhook


  def streamToWebhookLogic(tickSource: Source[Unit, _]) =
    tickSource
      .flatMapConcat: _ =>
        Source.unfoldAsync(()): _ =>
          repo.pullOutstanding
            .map:
              case Some(wi) => Some(((), wi))
              case None     => None
            .recoverWith:
              case ex =>
                logger.error(s"Error: unable to pull outstanding work item from mongo", ex)
                Future.successful(None) // Stream fails here, hits retry .andThen
      .mapAsync(1): wi =>
        forwardWorkGithubRequest(wi.item)
          .flatMap(_ => repo.completeAndDelete(wi.id))
          .recoverWith:
            case e if wi.failureCount < 3 => logger.error(s"${processingStatusFailedLog(wi)} Reason: ${e.getMessage}")
                                             repo.markAs(wi.id, ProcessingStatus.Failed)
            case e                        => logger.error(s"${processingStatusPermanentlyFailedLog(wi)} Reason: ${e.getMessage}")
                                             repo.markAs(wi.id, ProcessingStatus.PermanentlyFailed)


//  streamToWebhookLogic(Source.tick(initialDelay = FiniteDuration(0, SECONDS), interval = FiniteDuration(5, SECONDS), tick = NotUsed))
//    .runWith(Sink.ignore)
//    .andThen: res =>
//      println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@")
//      logger.info(s"Webhook stream terminated: $res - restarting")
//      streamToWebhookLogic(Source.tick(initialDelay = FiniteDuration(0, SECONDS), interval = FiniteDuration(5, SECONDS), tick = NotUsed))

end WebhookStreamRunner
