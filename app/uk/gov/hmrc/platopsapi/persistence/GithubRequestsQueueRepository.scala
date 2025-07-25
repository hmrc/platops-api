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

package uk.gov.hmrc.platopsapi.persistence

import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{WorkItem, WorkItemFields, WorkItemRepository}
import uk.gov.hmrc.platopsapi.webhook.GithubWebhookRequest

import java.time.{Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GithubRequestsQueueRepository @Inject()(
  configuration : Configuration
, mongoComponent: MongoComponent
)(using
  ExecutionContext
) extends WorkItemRepository[GithubWebhookRequest](
  collectionName = "githubRequestsQueue"
, mongoComponent = mongoComponent
, itemFormat     = GithubWebhookRequest.mongoFormat
, workItemFields = WorkItemFields.default
):

  override lazy val requiresTtlIndex: Boolean = false
  
  override def now(): Instant =
    Instant.now()

  lazy val retryIntervalMillis: Long =
    configuration.getMillis("queue.retryAfter")

  override val inProgressRetryAfter: Duration =
    Duration.ofMillis(retryIntervalMillis)

  def pullOutstanding: Future[Option[WorkItem[GithubWebhookRequest]]] =
    super.pullOutstanding(
      failedBefore    = now().minusMillis(retryIntervalMillis.toInt),
      availableBefore = now()
    )
