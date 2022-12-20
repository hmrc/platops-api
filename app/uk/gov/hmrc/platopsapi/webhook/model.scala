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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import java.net.{URLEncoder, URL}

sealed trait GithubRequest

object GithubRequest {

  private def toArchiveUrl(rawArchiveUrl: String, branchRef: String): URL =
    new URL(
      rawArchiveUrl
        .replace("{archive_format}", "zipball")
        .replace("{/ref}", s"/refs/heads/${URLEncoder.encode(branchRef, "UTF-8")}")
    )

  private def toBlobUrl(rawHtmlUrl: String, branchRef: String): URL =
    new URL(
      rawHtmlUrl
        .split("/pull/")
        .headOption
        .map(_ + "/blob/" + URLEncoder.encode(branchRef, "UTF-8"))
        .getOrElse("")
    )

  // https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#pull_request
  final case class PullRequest(
    id        : Long,
    repoName  : String,
    archiveUrl: java.net.URL,
    blobUrl   : java.net.URL,
    isPrivate : Boolean,
    branchRef : String,
    diffUrl   : String,
    action    : Option[String] = None,
    author    : String
  ) extends GithubRequest

  val readsPullRequest: Reads[GithubRequest] =
    (__ \ "pull_request" \ "head" \ "ref").read[String].flatMap { branchRef =>
      ( (__ \ "pull_request" \ "number"                     ).read[Long]
      ~ (__ \ "pull_request" \ "head" \ "repo" \ "name"     ).read[String]
      ~ (__ \ "repository"   \ "archive_url"                ).read[String].map(toArchiveUrl(_, branchRef))
      ~ (__ \ "pull_request" \ "html_url"                   ).read[String].map(toBlobUrl(_, branchRef))
      ~ (__ \ "pull_request" \ "head" \ "repo" \ "private"  ).read[Boolean]
      ~ Reads.pure(branchRef)
      ~ (__ \ "pull_request" \ "diff_url"                   ).read[String]
      ~ (__ \ "action"                                      ).read[String].map(Some(_)) // action is expected here
      ~ (__ \ "pull_request" \ "user" \ "url"               ).read[String].map(_.split("/").last)
      )(PullRequest.apply _)
    }

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
  final case class Push(
    repoName      : String,
    isPrivate     : Boolean,
    isArchived    : Boolean,
    authorName    : String,
    branchRef     : String,
    repositoryUrl : String,
    commitId      : String,
    archiveUrl    : String,
    deleted       : Boolean,
  ) extends GithubRequest

  val readsPush: Reads[GithubRequest] =
    ( (__ \ "repository" \ "name"       ).read[String]
    ~ (__ \ "repository" \ "private"    ).read[Boolean]
    ~ (__ \ "repository" \ "archived"   ).read[Boolean]
    ~ (__ \ "pusher"     \ "name"       ).read[String]
    ~ (__ \ "ref"                       ).read[String].map(_.stripPrefix("refs/heads/"))
    ~ (__ \ "repository" \ "url"        ).read[String]
    ~ (__ \ "after"                     ).read[String]
    ~ (__ \ "repository" \ "archive_url").read[String]
    ~ (__ \ "deleted"                   ).read[Boolean]
    )(Push.apply _)

  // https://docs.github.com/en/github-ae@latest/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#ping
  final case class Ping(
    zen: String
  ) extends GithubRequest

  private val readsPing: Reads[GithubRequest] =
    (__ \ "zen").read[String].map(Ping(_))

  val reads: Reads[GithubRequest] =
    readsPullRequest
      .orElse(readsPush)
      .orElse(readsPing)
}
