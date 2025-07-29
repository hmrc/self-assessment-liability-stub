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

import models.HipResponse

import java.time.LocalDate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}

class HipControllerSpec extends AnyWordSpec with Matchers {
  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new HipController(Helpers.stubControllerComponents())
  private val validUtr: String = "1234567890"
  private val validFromDate: String = "2024-01-01"
  private val validToDate: String = LocalDate.now().toString

  "GET /" should {
    "return 200 with correctly formatted details for any valid UTR" in {
      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(fakeRequest)
      status(result) shouldBe Status.OK
      // Check that the response JSON can be converted to a HipResponse.
      Json.fromJson[HipResponse](contentAsJson(result)).get
    }

    "return 400 BAD_REQUEST with correct error message for invalid correlation ID" in {
      val result =
        controller.getSelfAssessmentData("0111111400", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Submission has not passed validation. Invalid Correlation Id."
      )
    }

    "return 401 UNAUTHORIZED with correct error message for invalid authentication credentials" in {
      val result =
        controller.getSelfAssessmentData("0111111401", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.UNAUTHORIZED
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid basic authentication credentials."
      )
    }

    "return 403 FORBIDDEN with correct error message when authority is denied" in {
      val result =
        controller.getSelfAssessmentData("0111111403", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.FORBIDDEN
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "User does not have authority to retrieve requested record."
      )
    }

    "return 404 NOT_FOUND with correct error message when UTR is not found" in {
      val result =
        controller.getSelfAssessmentData("0111111404", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Identifier not found."
      )
    }

    "return 422 UNPROCESSABLE_ENTITY with correct error message for invalid UTR" in {
      val result =
        controller.getSelfAssessmentData("0111111422", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.UNPROCESSABLE_ENTITY
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid utr entered."
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message for general internal server errors" in {
      val result =
        controller.getSelfAssessmentData("0111111500", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("message" -> "Internal server error.")
    }

    "return 502 BAD_GATEWAY with correct error message for service communication errors" in {
      val result =
        controller.getSelfAssessmentData("0111111502", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_GATEWAY
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Error communicating with external service."
      )
    }

    "return 503 SERVICE_UNAVAILABLE with correct error message when service unavailable" in {
      val result =
        controller.getSelfAssessmentData("0111111503", validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.SERVICE_UNAVAILABLE
      contentAsJson(result) shouldBe Json.obj("message" -> "Service unavailable")
    }
  }
}
