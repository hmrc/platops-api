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

import org.apache.commons.codec.digest.{HmacAlgorithms, HmacUtils}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WebhookControllerSpec extends AnyWordSpec with Matchers {

  "Webhook request signature" should {
    "pass if signature is correct" in {
      WebhookController.isSignatureValid(
        payload     = """{"foo": "bar"}""",
        secret      = "1234",
        ghSignature = "sha256=" + new HmacUtils(HmacAlgorithms.HMAC_SHA_256, "1234").hmacHex("""{"foo": "bar"}""")
      ) shouldBe true
    }

    "fail if signature is incorrect" in {
      WebhookController.isSignatureValid(
        payload     = """{"foo": "bar"}""",
        secret      = "1234",
        ghSignature = "sha256=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
      ) shouldBe false
    }
  }
}
