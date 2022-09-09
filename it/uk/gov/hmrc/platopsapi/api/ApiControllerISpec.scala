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

package uk.gov.hmrc.platopsapi.api

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

import play.api.libs.ws.WSClient
import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.Instant

class ApiControllerISpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneServerPerSuite {

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"

  "GET /api/v2/teams" should {
    case class TeamName(name: String, createdDate: Instant, lastActiveDate: Instant, repos: Int)

    implicit val reads: Reads[TeamName] =
      ( (__ \ "name"          ).read[String]
      ~ (__ \ "createdDate"   ).read[Instant]
      ~ (__ \ "lastActiveDate").read[Instant]
      ~ (__ \ "repos"         ).read[Int]
      )(TeamName.apply _)

    "return a list of teams" in {
      val response = wsClient.url(s"$baseUrl/api/v2/teams").get().futureValue
      response.status shouldBe 200
      Json.parse(response.body).as[List[TeamName]]
    }
  }
}
