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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.{Done, stream}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.mvc.Results.Ok
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.platopsapi.persistence.GithubRequestsQueueRepository

import scala.concurrent.{ExecutionContextExecutor, Future}

class WebhookStreamRunnerSpec
  extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with IntegrationPatience:

  implicit val system      : ActorSystem              = ActorSystem("TestSystem")
  implicit val ec          : ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer             = Materializer(system)

  "WebhookStreamRunner" should:
    "forward work items for multiple ticks" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], any[ProcessingStatus], any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Ok))

      onTest.run(Source.repeat(()).take(2)).futureValue shouldBe Done

      verify(mockRepo     , times(5)).pullOutstanding
      verify(mockRepo     , times(3)).completeAndDelete(workItem.id)
      verify(mockConnector, times(3)).webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier])

    "forward multiple work items in a single tick" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Succeeded), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Ok))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo     , times(4)).pullOutstanding
      verify(mockRepo     , times(3)).completeAndDelete(workItem.id)
      verify(mockConnector, times(3)).webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier])

    "poll empty work item repository" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(None))
        .thenReturn(Future.successful(None))

      onTest.run(Source.repeat(()).take(2)).futureValue shouldBe Done

      verify(mockRepo     , times(2)).pullOutstanding
      verify(mockConnector, never() ).webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier])

    "mark work item as failed after a retry" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem.copy(failureCount = 1))))
        .thenReturn(Future.successful(None))
      when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("Webhook failed")))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Failed), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo, times(1)).markAs(workItem.id, ProcessingStatus.Failed)
      verify(mockRepo, never() ).completeAndDelete(any[ObjectId])

    "mark item as permanently failed after max retries" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem.copy(failureCount = 3))))
        .thenReturn(Future.successful(None))
      when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("Webhook failed")))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.PermanentlyFailed), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo, times(1)).markAs(workItem.id, ProcessingStatus.PermanentlyFailed)
      verify(mockRepo, never() ).completeAndDelete(any[ObjectId])

    "handle repository failures by restarting the stream" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.failed(RuntimeException("Database error")))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Succeeded), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Ok))

      onTest.run(Source.repeat(()).take(2)).futureValue shouldBe Done

      verify(mockRepo     , times(3)).pullOutstanding
      verify(mockRepo     , times(1)).completeAndDelete(workItem.id)
      verify(mockConnector, times(1)).webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier])

    "call webhook connector using values from pulled work item request" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Succeeded), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockConnector.webhook(eqTo(workItem.item.targetUrl), eqTo(workItem.item.payload), eqTo(WebhookEvent.Repository))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Ok))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo     , times(2)).pullOutstanding
      verify(mockRepo     , times(1)).completeAndDelete(workItem.id)
      verify(mockConnector, times(1)).webhook(eqTo(workItem.item.targetUrl), eqTo(workItem.item.payload), eqTo(WebhookEvent.Repository))(any[HeaderCarrier])

  trait Setup:
    val mockConfig: Configuration =
      Configuration(
        "webhook-stream.enabled"                  -> "false"
      , "webhook-stream.source-tick.initialDelay" -> "1.second"
      , "webhook-stream.source-tick.interval"     -> "1.second"
      , "queue.retryInterval"                     -> "1.second"
      )
    val mockRepo     : GithubRequestsQueueRepository  = mock[GithubRequestsQueueRepository]
    val mockConnector: WebhookConnector               = mock[WebhookConnector]
    val onTest       : WebhookStreamRunner            = WebhookStreamRunner(mockRepo, mockConnector, mockConfig)

    val workItem: WorkItem[GithubWebhookRequest] =
      WorkItem(
        id           = new ObjectId()
      , receivedAt   = java.time.Instant.now()
      , updatedAt    = java.time.Instant.now()
      , availableAt  = java.time.Instant.now()
      , status       = ProcessingStatus.ToDo
      , failureCount = 0
      , item         = GithubWebhookRequest(WebhookEvent.Repository, "testTargetUrl", """{"test": "payload"}""")
      )
