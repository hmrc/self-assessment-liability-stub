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

package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import utils.constants.RequestResponseConstants.{badNinoServerError, invalidNino}

class MtdIdLookupControllerSpec extends AnyWordSpec with Matchers {

  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new MtdIdLookupController(Helpers.stubControllerComponents())

  "GET /" should {
    "return 200 with correct mtdbsa for a valid nino" in {
      val result = controller.getMtdId("ss686868d")(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.obj("mtdbsa" -> "XQIT00000000001")
    }

    "return 400 BAD_REQUEST with correct error message for invalid nino" in {
      val result = controller.getMtdId(invalidNino)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid national insurance number supplied"
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message when service unavailable" in {
      val result = controller.getMtdId(badNinoServerError)(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("message" -> "Service currently unavailable")
    }
  }
}
