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


sealed trait WebhookEvent { def asString: String }

// asString matches X-GitHub-Event https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads
object WebhookEvent {
  case object Pull        extends WebhookEvent { val asString = "pull_request"}
  case object Push        extends WebhookEvent { val asString = "push"        }
  case object Repository  extends WebhookEvent { val asString = "repository"  }
  case object Ping        extends WebhookEvent { val asString = "ping"        }
  case object Team        extends WebhookEvent { val asString = "team"        }

  val values: List[WebhookEvent] =
    List(Pull, Push, Repository, Ping, Team)

  def parse(s: String): Either[String, WebhookEvent] =
    values.find(_.asString == s) match {
      case Some(x) => Right(x)
      case None    => Left(s)
    }
}
