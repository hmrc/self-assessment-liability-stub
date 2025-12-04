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
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import utils.constants.RequestResponseConstants.{
  invalidNinoBadRequest,
  invalidNinoETMPValidationError,
  invalidNinoServiceUnavailable,
  validMtditid
}

class MtdIdLookupControllerSpec extends AnyWordSpec with Matchers {

  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new MtdIdLookupController(Helpers.stubControllerComponents())

  private val errorCodeMap = Map(
    "001" -> "REGIME missing or invalid",
    "006" -> "Subscription data not found",
    "007" -> "Your request cannot be processed, please contact the help line",
    "008" -> "ID not found"
  )

  "GET /" should {
    "return 200 with correct mtdId for a valid nino" in {
      val result = controller.getMtdId("ss686868d")(fakeRequest)
      status(result) shouldBe Status.OK
      val json = contentAsJson(result)

      (json \ "success" \ "taxPayerDisplayResponse" \ "mtdId").as[String] shouldBe validMtditid
    }

    "return 400 BAD_REQUEST with correct error message for invalid nino" in {
      val result = controller.getMtdId(invalidNinoBadRequest)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "origin").as[String] shouldBe "HIP"
      (json \ "response" \ 0 \ "type").as[String] shouldBe "BAD_REQUEST"
      (json \ "response" \ 0 \ "reason").as[String] shouldBe "Invalid request format or parameters."
    }

    "return 503 SERVICE_UNAVAILABLE with correct error message when service is unavailable" in {
      val result = controller.getMtdId(invalidNinoServiceUnavailable)(fakeRequest)
      status(result) shouldBe Status.SERVICE_UNAVAILABLE
      val json = contentAsJson(result)
      (json \ "origin").as[String] shouldBe "HIP"
      (json \ "response" \ 0 \ "type").as[String] shouldBe "SERVICE_UNAVAILABLE"
      (json \ "response" \ 0 \ "reason")
        .as[String] shouldBe "Service is currently unavailable."
    }

    "return error response with random error code and description" in {
      val result = controller.getMtdId(invalidNinoETMPValidationError)(fakeRequest)
      status(result) shouldBe Status.UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)

      val code = (json \ "errors" \ "code").as[String]
      val text = (json \ "errors" \ "text").as[String]

      errorCodeMap should contain key code
      errorCodeMap(code) shouldBe text
    }

    "return 200 OK with success response for valid nino" in {
      val validNino = "AS243900B"
      val result = controller.getMtdId(validNino)(fakeRequest)
      status(result) shouldBe Status.OK
      val json = contentAsJson(result)

      (json \ "success" \ "taxPayerDisplayResponse" \ "nino").as[String] shouldBe validNino
      (json \ "success" \ "taxPayerDisplayResponse" \ "mtdId").as[String] shouldBe validMtditid
    }
  }
}
