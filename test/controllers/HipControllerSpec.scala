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
import org.scalatest.matchers.must.Matchers.mustEqual
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import services.HipService
import utils.constants.RequestResponseConstants.*

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class HipControllerSpec extends AnyWordSpec with Matchers {
  private val mockService: HipService = mock[HipService]
  private val correlationId = UUID.randomUUID.toString
  private val fakeRequest = FakeRequest("GET", "/")
    .withHeaders(
      "Authorization" -> "Basic dGVzdDp0ZXN0",
      "Content-Type" -> "application/json",
      "CorrelationId" -> correlationId
    )
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

    "return 200 with incorrectly formatted details for earliestPayableDueDate and earliestPendingDueDate" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipInternalServiceError, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.OK
      val json = contentAsJson(result)
      (json \ "balanceDetails" \ "earliestPayableDueDate").asOpt[String] shouldBe None
      (json \ "balanceDetails" \ "earliestPendingDueDate").asOpt[String] shouldBe None

      (json \ "balanceDetails" \ "totalPayableBalance").as[Int] should be > 0
      (json \ "balanceDetails" \ "totalPendingBalance").as[Int] should be > 0
    }

    "return 200 with correctly formatted details for earliestPayableDueDate and earliestPendingDueDate" in {
      val result =
        controller.getSelfAssessmentData(goodUtrHipInternalService, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.OK
      val json = contentAsJson(result)
      (json \ "balanceDetails" \ "earliestPayableDueDate").asOpt[String] should not be None
      (json \ "balanceDetails" \ "earliestPendingDueDate").asOpt[String] should not be None

      (json \ "balanceDetails" \ "totalPayableBalance").as[Int] should be > 0
      (json \ "balanceDetails" \ "totalPendingBalance").as[Int] should be > 0
    }

    "return 200 with only balance details in payload for a set utr" in {
      val result =
        controller.getSelfAssessmentData(utrWithOnlyBalanceDetails, validFromDate, validToDate)(
          fakeRequest
        )
      val minimalHipResponseJson: JsValue = Json.obj(
        "balanceDetails" -> Json.obj(
          "totalOverdueBalance" -> 0,
          "totalPayableBalance" -> 0,
          "totalPendingBalance" -> 0,
          "totalBalance" -> 0,
          "totalCreditAvailable" -> 0
        )
      )
      status(result) shouldBe OK
      contentAsJson(result) mustEqual minimalHipResponseJson
    }

    "return 400 BAD_REQUEST with correct error message for invalid correlation ID" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipInvalidCorrelationId, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe
        Json.toJson(
          createErrorResponse(
            "HIP",
            None,
            "INVALID_CORRELATIONID",
            "Submission has not passed validation. Invalid CorrelationId."
          )
        )
    }

    "return 400 BAD_REQUEST with correct error response for invalid dates" in {
      when(mockService.generateHipResponse("2-20-2023", "2-20-2024"))
        .thenThrow(new DateTimeParseException("Parse failed", "bad-date", 0))
      val result = controller.getSelfAssessmentData(validUtr, "2-20-2023", "2-20-2024")(fakeRequest)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HIP",
          None,
          "INVALID_DATE_FORMAT",
          "Invalid date inputted. The date needs to follow YYYY-MM-DD format"
        )
      )
    }

    "return 401 UNAUTHORIZED with correct error response for invalid authentication credentials" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipUnauthorised, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HIP",
          None,
          "UNAUTHORIZED",
          "Invalid basic authentication credentials."
        )
      )
    }

    "return 403 FORBIDDEN with correct error response when authority is denied" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipForbidden, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.FORBIDDEN
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HoD",
          Some("ITSA Repayments Viewer"),
          "FORBIDDEN",
          "User does not have authority to retrieve requested record."
        )
      )
    }

    "return 404 NOT_FOUND with correct error response when UTR is not found" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipUtrNotFound, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HoD",
          Some("ITSA Repayments Viewer"),
          "NOT_FOUND",
          "Identifier not found."
        )
      )
    }

    "return 422 UNPROCESSABLE_ENTITY with correct error response for invalid UTR" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipUtrInvalid, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.UNPROCESSABLE_ENTITY
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse("HoD", Some("ITSA Repayments Viewer"), "48003", "Invalid UTR entered.")
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error response for general internal server errors" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipServerError, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse("HIP", None, "SERVER_ERROR", "Internal server error.")
      )
    }

    "return 502 BAD_GATEWAY with correct error response for service communication errors" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipExternalServiceError, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_GATEWAY
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HoD",
          Some("SA Balance and Transaction details"),
          "BAD_GATEWAY",
          "Error communicating with external service."
        )
      )
    }

    "return 503 SERVICE_UNAVAILABLE with correct error response when service unavailable" in {
      val result =
        controller.getSelfAssessmentData(badUtrHipServiceUnavailable, validFromDate, validToDate)(
          fakeRequest
        )
      status(result) shouldBe Status.SERVICE_UNAVAILABLE
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse("HIP", None, "SERVICE_UNAVAILABLE", "Service unavailable")
      )
    }

    "return 400 when Authorization header is missing" in {
      val requestWithoutAuth = FakeRequest("GET", "/")
        .withHeaders(
          "Content-Type" -> "application/json",
          "CorrelationId" -> correlationId
        )

      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(requestWithoutAuth)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse("HIP", None, "MISSING_HEADER", "Authorization header is required")
      )
    }

    "return 400 when CorrelationId header is missing" in {
      val requestWithoutAuth = FakeRequest("GET", "/")
        .withHeaders(
          "Authorization" -> "Basic dGVzdDp0ZXN0",
          "Content-Type" -> "application/json"
        )

      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(requestWithoutAuth)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HIP",
          None,
          "MISSING_HEADER",
          "Correlation-ID header is required and must be in UUID format"
        )
      )
    }

    "return 400 when Content-Type header is missing" in {
      val requestWithoutAuth = FakeRequest("GET", "/")
        .withHeaders(
          "CorrelationId" -> correlationId,
          "Authorization" -> "Basic dGVzdDp0ZXN0"
        )

      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(requestWithoutAuth)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse("HIP", None, "MISSING_HEADER", "Content-Type header is required")
      )
    }

    "return 400 when correlationId not in UUID format" in {
      val requestWithoutAuth = FakeRequest("GET", "/")
        .withHeaders(
          "Content-Type" -> "application/json",
          "CorrelationId" -> "test-correlation-id",
          "Authorization" -> "Basic dGVzdDp0ZXN0"
        )

      val result =
        controller.getSelfAssessmentData(validUtr, validFromDate, validToDate)(requestWithoutAuth)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(
        createErrorResponse(
          "HIP",
          None,
          "MISSING_HEADER",
          "Correlation-ID header is required and must be in UUID format"
        )
      )
    }
  }
}

private def createErrorResponse(
    origin: String,
    service: Option[String],
    errorType: String,
    reason: String
): HipResponseError = {
  HipResponseError(
    origin = origin,
    service = service,
    response = HipErrorDetails(
      failures = List(HipError(errorType, reason))
    )
  )
}
object HipControllerSpec {

  val sampleHipResponse: HipResponse = HipResponse(
    balanceDetails = BalanceDetails(
      totalOverdueBalance = 1000.00,
      totalPayableBalance = 500.00,
      earliestPayableDueDate = Some(LocalDate.of(2024, 2, 15)),
      totalPendingBalance = 200.00,
      earliestPendingDueDate = Some(LocalDate.of(2024, 6, 15)),
      totalBalance = 1700.00,
      totalCreditAvailable = 0.00,
      codedOutDetail = List.empty[CodedOutDetail]
    ),
    chargeDetails = List(
      ChargeDetails(
        chargeId = "charge-123",
        creationDate = LocalDate.of(2023, 1, 15),
        chargeType = "ITSA",
        chargeAmount = 1000.00,
        taxYear = "2023-2024",
        dueDate = LocalDate.of(2024, 1, 31),
        amendments = List.empty,
        outstandingAmount = 1000.00,
        accruingInterest = None,
        accruingInterestRate = None
      )
    ),
    refundDetails = List.empty,
    paymentHistoryDetails = List(
      PaymentHistoryDetails(
        paymentAmount = 500.00,
        paymentReference = Some("payment-123"),
        paymentMethod = Some("bank transfer"),
        paymentDate = LocalDate.of(2023, 2, 15),
        processedDate = Some(LocalDate.of(2023, 2, 16)),
        allocationReference = Some("charge-123")
      )
    )
  )
}
