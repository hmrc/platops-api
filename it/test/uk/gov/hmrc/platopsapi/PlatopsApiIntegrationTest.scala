/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.platopsapi

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Suites}
import uk.gov.hmrc.platopsapi.stub.TestStubs

import scala.concurrent.ExecutionContext

class PlatopsApiIntegrationTest extends Suites(
  new ApiControllerISpec
//, new LegacyControllerISpec
) with BeforeAndAfterAll with ScalaFutures with IntegrationPatience {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def beforeAll(): Unit = {
    TestStubs.resetAll()
    super.beforeAll()
  }
}
