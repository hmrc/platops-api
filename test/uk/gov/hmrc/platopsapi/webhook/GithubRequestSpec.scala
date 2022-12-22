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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import java.net.URL

class GithubRequestSpec extends AnyWordSpec with Matchers {

  private def loadResource(filename: String) = {
    val source = scala.io.Source.fromURL(getClass.getResource(filename))
    try source.mkString finally source.close()
  }

  "GithubRequest" should {
    "parse Pull Request" in {
      Json
        .parse(loadResource("/pull-request.json"))
        .as[GithubRequest](GithubRequest.reads) shouldBe (
          GithubRequest.PullRequest(
            id         = 2,
            repoName   = "Hello-World",
            archiveUrl = new URL("https://api.github.com/repos/Codertocat/Hello-World/zipball/refs/heads/changes"),
            blobUrl    = new URL("https://github.com/Codertocat/Hello-World/blob/changes"),
            isPrivate  = false,
            branchRef  = "changes",
            diffUrl    = "https://github.com/Codertocat/Hello-World/pull/2.diff",
            action     = Some("opened"),
            author     = "Codertocat"
          )
        )
    }

    "parse Push" in {
      Json
        .parse(loadResource("/push.json"))
        .as[GithubRequest](GithubRequest.reads) shouldBe (
          GithubRequest.Push(
            repoName      = "Hello-World",
            isPrivate     = false,
            isArchived    = false,
            authorName    = "SomeUser",
            branchRef     = "master",
            repositoryUrl = "https://github.com/Codertocat/Hello-World",
            commitId      = "12d4f5a69fe313fd9c45eb43e99069c38dc612fe",
            archiveUrl    = "https://api.github.com/repos/Codertocat/Hello-World/{archive_format}{/ref}",
            deleted       = false
          )
        )
    }

    "parse Delete" in {
      Json
        .parse(loadResource("/delete.json"))
        .as[GithubRequest](GithubRequest.reads) shouldBe (
          GithubRequest.Delete(
            repoName      = "Hello-World",
            authorName    = "SomeUser",
            branchRef     = "master",
            repositoryUrl = "https://github.com/Codertocat/Hello-World"
          )
        )
    }

    "parse Repository" in {
      Json
        .parse(loadResource("/repository.json"))
        .as[GithubRequest](GithubRequest.reads) shouldBe (
          GithubRequest.Repository(
            repoName = "Hello-World",
            action   = "created"
          )
        )
    }

    "parse Ping" in {
      Json
        .parse(loadResource("/ping.json"))
        .as[GithubRequest](GithubRequest.reads) shouldBe (
          GithubRequest.Ping(
            zen = "Anything added dilutes everything else."
         )
        )
    }
  }
}
