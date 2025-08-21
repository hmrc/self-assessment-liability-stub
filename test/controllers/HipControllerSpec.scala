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

import controllers.HipControllerSpec.sampleHipResponse
import models.*
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status
import play.api.libs.json.{JsResultException, Json}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import services.HipService
import utils.constants.RequestResponseConstants.*

import java.time.LocalDate

class HipControllerSpec extends AnyWordSpec with Matchers {
  private val mockService: HipService = mock[HipService]
  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new HipController(Helpers.stubControllerComponents(), mockService)
  private val validUtr: String = "1234567890"
  private val validFromDate: String = "2024-01-01"
  private val validToDate: String = LocalDate.now().toString

  "GET /" should {
    "return 200 with correctly formatted details for any valid UTR" in {
      when(mockService.generateHipResponse(validFromDate, validToDate))
        .thenReturn(sampleHipResponse)
      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(sampleHipResponse)
    }

    "return 400 BAD_REQUEST with correct error message for invalid correlation ID" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipInvalidCorrelationId, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Submission has not passed validation. Invalid Correlation Id."
      )
    }

    "return 400 BAD_REQUEST with correct error message for invalid dates" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipInvalidCorrelationId, "2-20-2023", "2-20-2024")(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Submission has not passed validation. Invalid Correlation Id."
      )
    }

    "return 401 UNAUTHORIZED with correct error message for invalid authentication credentials" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipUnauthorised, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.UNAUTHORIZED
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid basic authentication credentials."
      )
    }

    "return 403 FORBIDDEN with correct error message when authority is denied" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipForbidden, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.FORBIDDEN
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "User does not have authority to retrieve requested record."
      )
    }

    "return 404 NOT_FOUND with correct error message when UTR is not found" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipUtrNotFound, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Identifier not found."
      )
    }

    "return 422 UNPROCESSABLE_ENTITY with correct error message for invalid UTR" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipUtrInvalid, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.UNPROCESSABLE_ENTITY
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid utr entered."
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message for general internal server errors" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipServerError, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("message" -> "Internal server error.")
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message if validation on the data returned from generator fails" in {
      when(mockService.generateHipResponse(validFromDate, validToDate))
        .thenThrow(JsResultException(Seq.empty))
      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("message" -> "Generated a bad response")
    }

    "return 502 BAD_GATEWAY with correct error message for service communication errors" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipExternalServiceError, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_GATEWAY
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Error communicating with external service."
      )
    }

    "return 503 SERVICE_UNAVAILABLE with correct error message when service unavailable" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipServiceUnavailable, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.SERVICE_UNAVAILABLE
      contentAsJson(result) shouldBe Json.obj("message" -> "Service unavailable")
    }
  }
}
object HipControllerSpec {

  val sampleHipResponse: HipResponse = HipResponse(
    balanceDetails = BalanceDetails(
      totalOverdueBalance = BigDecimal("1000.00"),
      totalPayableBalance = BigDecimal("500.00"),
      earliestPayableDueDate = Some(LocalDate.of(2024, 2, 15)),
      totalPendingBalance = BigDecimal("200.00"),
      earliestPendingDueDate = Some(LocalDate.of(2024, 6, 15)),
      totalBalance = BigDecimal("1700.00"),
      totalCreditAvailable = BigDecimal("0.00"),
      codedOutDetail = List.empty[CodedOutDetail]
    ),
    chargeDetails = List(
      ChargeDetails(
        chargeId = "charge-123",
        creationDate = LocalDate.of(2023, 1, 15),
        chargeType = "ITSA",
        chargeAmount = BigDecimal("1000.00"),
        taxYear = "2023-2024",
        dueDate = LocalDate.of(2024, 1, 31),
        amendments = List.empty,
        outstandingAmount = BigDecimal("1000.00"),
        outstandingInterestDue = None,
        accruingInterest = None,
        accruingInterestPeriod = None,
        accruingInterestRate = None
      )
    ),
    refundDetails = List.empty,
    paymentHistoryDetails = List(
      PaymentHistoryDetails(
        paymentAmount = BigDecimal("500.00"),
        paymentReference = "payment-123",
        paymentMethod = Some("bank transfer"),
        paymentDate = LocalDate.of(2023, 2, 15),
        processedDate = Some(LocalDate.of(2023, 2, 16)),
        allocationReference = List("charge-123")
      )
    )
  )
}
