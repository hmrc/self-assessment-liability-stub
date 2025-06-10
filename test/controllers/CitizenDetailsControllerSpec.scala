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
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}

class CitizenDetailsControllerSpec extends AnyWordSpec with Matchers {

  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new CitizenDetailsController(Helpers.stubControllerComponents())

  "GET /" should {
    "return 200 with correct NINO for a valid UTR" in {
      val result = controller.getNino("any other UTR")(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse(controller.generateResponse(CitizenDetailsController.validNino))
    }

    "return 400 BAD_REQUEST with correct error message for invalid UTR" in {
      val result = controller.getNino(CitizenDetailsController.badUtrInvalid)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid SaUtr."
      )
    }

    "return 404 NOT_FOUND with correct error message for no matching UTR" in {
      val result = controller.getNino(CitizenDetailsController.badUtrNone)(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "No record for the given SaUtr is found."
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message for multiple matching UTRs" in {
      val result = controller.getNino(CitizenDetailsController.badUtrMultiple)(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "More than one valid matching result."
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message when service unavailable" in {
      val result = controller.getNino(CitizenDetailsController.badUtrServerError)(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Service currently unavailable"
      )
    }

    "return 200 with invalid NINO for a specific UTR" in {
      val result = controller.getNino(CitizenDetailsController.badUtrNinoInvalid)(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse(controller.generateResponse(MtdIdLookupController.badNinoInvalid))
    }

    "return 200 with server error NINO for a specific UTR" in {
      val result = controller.getNino(CitizenDetailsController.badUtrNinoServerError)(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse(controller.generateResponse(MtdIdLookupController.badNinoServerError))
    }
  }
}
