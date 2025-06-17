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

import model.HipQuery
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}

class HipControllerSpec extends AnyWordSpec with Matchers with StubData {
  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new HipController(Helpers.stubControllerComponents())
  private val validRequest: HipQuery = HipQuery("0000000200", "01012025", "01012025")
  private val invalidRequest: HipQuery = HipQuery("0111111500", "01012025", "01012025")

  "GET /" should {
    "return 400 BAD_REQUEST with correct error message for invalid request JSON format" in {
      val result =
        controller.getSelfAssessmentData()(fakeRequest.withBody(Json.toJson("invalid JSON.")))
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Body of the request is not in the correct format"
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message when service unavailable" in {
      val result =
        controller.getSelfAssessmentData()(fakeRequest.withBody(Json.toJson(invalidRequest)))
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("message" -> "Service currently unavailable")
    }

    "return 200 with valid response JSON for a valid request" in {
      val result =
        controller.getSelfAssessmentData()(fakeRequest.withBody(Json.toJson(validRequest)))
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(validHipJsonResponse)
    }
  }
}
