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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream
import org.apache.pekko.{NotUsed, Done}
import org.apache.pekko.stream.scaladsl.{Source, Sink}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar

import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.platopsapi.persistence.GithubRequestsQueueRepository
import uk.gov.hmrc.platopsapi.webhook.WebhookEvent.Repository
import uk.gov.hmrc.http.HeaderCarrier

import play.api.mvc.Results.Ok

import org.bson.types.ObjectId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future


class WebhookStreamRunnerSpec
  extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll:


  implicit val system      : ActorSystem              = ActorSystem("TestSystem")
  implicit val ec          : ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer             = Materializer(system)

  val mockRepo     : GithubRequestsQueueRepository = mock[GithubRequestsQueueRepository]
  val mockConnector: WebhookConnector              = mock[WebhookConnector]


  val workItem = WorkItem(
    id = new ObjectId(),
    receivedAt = java.time.Instant.now(),
    updatedAt = java.time.Instant.now(),
    availableAt = java.time.Instant.now(),
    status = ProcessingStatus.ToDo,
    failureCount = 0,
    item = GithubWebhookRequest(WebhookEvent.Repository, "", "")
  )

  override def afterAll(): Unit =
    system.terminate()
    super.afterAll()

  import org.mockito.Mockito.{atLeast => atLeastMockito, verify}


  "WebhookStreamRunner" should {

    "process work items successfully" in {

      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))

      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))


      when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Ok))


      val onTest = WebhookStreamRunner(mockRepo, mockConnector)


      val testSource = Source.single(()).take(3)

      val result: Future[Done] = onTest.streamToWebhookLogic(testSource).runWith(Sink.ignore)

      result.failed.futureValue shouldBe Done

      verify(mockRepo, times(4)).pullOutstanding
      verify(mockRepo, times(3)).completeAndDelete(workItem.id)
      verify(mockConnector, times(3)).webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier])

    }
  }


//
//  val onTest: WebhookStreamRunner = WebhookStreamRunner(mockRepo, mockConnector)
//
//
//  import org.mockito.Mockito.{atLeast => atLeastMockito, verify}
//  import java.util.concurrent.atomic.AtomicInteger
//
//
//  val callCount = new AtomicInteger(0)
//
//  when(mockRepo.pullOutstanding(any(), any())).thenAnswer(_ => {
//    if (callCount.incrementAndGet() <= 2) Future.successful(Some(workItem))
//    else Future.successful(None)
//  })
//
//  when(mockRepo.completeAndDelete(any())).thenReturn(Future.successful(true))
//  when(mockConnector.webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier]))
//    .thenReturn(Future.successful(Ok))
//
//  val runner = WebhookStreamRunner(mockRepo, mockConnector)
//
//  "streamToWebhook2" should {
//    "pull work items and forward them" in {
//      val source: Source[Unit, _] =
//        Source
//          .tick(FiniteDuration(1, SECONDS), FiniteDuration(5, SECONDS), NotUsed)
//          .take(3)
//          .map(_ => ())
//
//      val resultF = runner.streamToWebhook2(source)
//
//      import scala.concurrent.duration._
//      Await.result(resultF, 3.seconds)
//
//      resultF.futureValue shouldBe Done
//
//      verify(mockRepo, atLeastMockito(2)).pullOutstanding
//      verify(mockConnector, atLeastMockito(2)).webhook(any[String], any[String], any[WebhookEvent])(any[HeaderCarrier])
//      verify(mockRepo, atLeastMockito(2)).completeAndDelete(any())
//    }
//  }
