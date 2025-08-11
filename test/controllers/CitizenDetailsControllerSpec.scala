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
import utils.constants.RequestResponseConstants.validNino

class CitizenDetailsControllerSpec extends AnyWordSpec with Matchers {

  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new CitizenDetailsController(Helpers.stubControllerComponents())

  "Generating a success response" should {
    "return a hard-coded response with the given string as the NINO" in {
      val expectedResponse: String =
        s"""
    {
      "name": {
        "current": {
          "firstName": "John",
          "lastName": "Smith"
        },
        "previous": []
      },
      "ids": {
        "nino": "123456789"
      },
      "dateOfBirth": "11121971"
    }
    """
      controller.generateSuccessResponse(List("123456789")) shouldBe expectedResponse
    }
  }

  "GET /" should {
    "return 200 with correct NINO for a valid UTR" in {
      val result = controller.getNino("any other UTR")(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse(
        controller.generateSuccessResponse(List("AA055075C"))
      )
    }

    "return 404 NOT_FOUND with correct error message for no matching UTR" in {
      val result = controller.getNino("1000000404")(fakeRequest)
      status(result) shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "No record for the given SaUtr is found."
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message for multiple matching UTRs" in {
      val result = controller.getNino("2000000500")(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.parse(
        controller.generateSuccessResponse(List(validNino, validNino))
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message when service unavailable" in {
      val result = controller.getNino("1777777200")(fakeRequest)
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Downstream Error"
      )
    }

    "return 200 with invalid NINO for a specific UTR" in {
      val result = controller.getNino("1666666200")(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse(
        controller.generateSuccessResponse(List("ss666666b"))
      )
    }

  }
}
