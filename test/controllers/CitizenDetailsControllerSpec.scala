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

import config.AppConfig
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import utils.constants.RequestResponseConstants.*

class CitizenDetailsControllerSpec extends AnyWordSpec with Matchers {

  private val fakeRequest = FakeRequest("GET", "/")
  val mockConfig: AppConfig = mock[AppConfig]
  private val controller =
    new CitizenDetailsController(Helpers.stubControllerComponents(), mockConfig)
  "CID Controller" when {
    "E2E toggle off" should {
      "return 200 with correct NINO for a valid UTR" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(false)
        val result = controller.getNino("any other UTR")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List(validNino1))
        )
      }

      "return 404 NOT_FOUND with correct error message for no matching UTR" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(false)
        val result = controller.getNino(noNinoFoundForUtr)(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
        contentAsJson(result) shouldBe Json.obj(
          "message" -> "No record for the given SaUtr is found."
        )
      }

      "return 500 INTERNAL_SERVER_ERROR with correct error message for multiple matching UTRs" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(false)
        val result = controller.getNino(badUtrMultiple)(fakeRequest)
        status(result) shouldBe Status.INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List(validNino1, validNino2))
        )
      }

      "return 200 for a utr that will result in server error in MTD look up service" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(false)
        val result = controller.getNino(badUtrNinoServerError)(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List(badNinoServerError))
        )
      }

      "return 200 with invalid NINO for a specific UTR" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(false)
        val result = controller.getNino(badUtrInvalidNino)(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List(invalidNino))
        )
      }
    }
    "E2E toggle on" should {
      "return 200 with WP105133A for 3384286946" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(true)
        val result = controller.getNino("3384286946")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List("WP105133A"))
        )
      }

      "return 200 with WP105333A for 1992665564" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(true)
        val result = controller.getNino("1992665564")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List("WP105333A"))
        )
      }

      "return 200 with WP105533A for 4809635190" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(true)
        val result = controller.getNino("4809635190")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List("WP105533A"))
        )
      }

      "return 200 with WP184333A for 2112635977" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(true)
        val result = controller.getNino("2112635977")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List("WP184333A"))
        )
      }

      "return 200 with WP120333A for 3601373390" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(true)
        val result = controller.getNino("3601373390")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List("WP120333A"))
        )
      }

      "return 200 with WP071433A for 1405365362" in {
        when(mockConfig.cidE2eTestDataEnabled).thenReturn(true)
        val result = controller.getNino("1405365362")(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsJson(result) shouldBe Json.parse(
          controller.generateSuccessResponse(List("WP071433A"))
        )
      }
    }
  }

}
