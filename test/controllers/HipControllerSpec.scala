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

class HipControllerSpec extends AnyWordSpec with Matchers {
  private val fakeRequest = FakeRequest("GET", "/")
  private val controller = new HipController(Helpers.stubControllerComponents())
  private val validUtr: String = "1234567890"
  private val validDateFrom: String = "2024-01-01"
  private val validHipJsonResponse2023: String = """{
  "balanceDetails": {
    "totalOverdueBalance": 500.00,
    "totalPayableBalance": 500.00,
    "payableDueDate": "2025-04-31",
    "totalPendingBalance": 1500.00,
    "pendingDueDate": "2025-07-15",
    "totalBalance": 2000.00,
    "totalCodedOut": 250.00,
    "totalCreditAvailable": 0.00
  },
  "chargeDetails": [
    {
      "chargeId": "AB1234567",
      "creationDate": "2025-01-15",
      "chargeType": "ITSA",
      "chargeAmount": 1250.00,
      "outstandingAmount": 500.00,
      "taxYear": "2024-2025",
      "dueDate": "2025-04-31",
      "interestAmountDue": 0.00,
      "accruingInterest": 0.00,
      "accruingInterestDateRange": [
        "interestStartDate": "2025-04-31",
        "interestEndDate": "2025-04-01"
      ],
      "accruingInterestRate": 0.00,
      "amendments": [
        {
          "amendmentDate": "2025-04-15",
          "amendmentAmount": 500.00,
          "amendmentReason": "money",
          "newChargeBalance": 500.00,
          "paymentMethod": "payment",
          "paymentDate": "2025-04-15"
        }
      ],
      "codedOutDetail": [
        {
          "amount": 250.00,
          "effectiveDate": "2025-04-01",
          "taxYear": "2024-2025",
          "effectiveTaxYear": "2025-2026"
        }
      ]
    },
    {
      "chargeId": "EF2345678",
      "creationDate": "2024-02-10",
      "chargeType": "NICS",
      "chargeAmount": 2200.00,
      "outstandingAmount": 0.00,
      "taxYear": "2023-2024",
      "dueDate": "2024-04-01",
      "amendments": [
        {
          "amendmentDate": "2024-04-31",
          "amendmentAmount": 200.00,
          "amendmentReason": "money"
        },
        {
          "amendmentDate": "2024-12-08",
          "amendmentAmount": 2058.33,
          "amendmentReason": "money"
        }
      ]
    },
    {
      "chargeId": "KL3456789",
      "creationDate": "2025-05-22",
      "chargeType": "VATC",
      "chargeAmount": 1500.00,
      "outstandingAmount": 1500.00,
      "taxYear": "2025-2026",
      "dueDate": "2025-07-15"
    }
  ],
  "refundDetails": [
    {
      "issueDate": "2024-01-10",
      "refundMethod": "bank_transfer",
      "refundRequestDate": "2023-12-12",
      "refundRequestAmount": 350,
      "refundReference": "REF123456",
      "interestAddedToRefund": 5.25,
      "refundActualAmount": 355.25,
      "refundStatus": "processed"
    }
  ],
  "paymentHistoryDetails": [
    {
      "paymentAmount": 500.00,
      "paymentReference": "payment reference id",
      "paymentMethod": "payment method",
      "paymentDate": "2025-04-11"
      "dateProcessed": "2025-04-15",
      "allocationReference": "allocation reference"
    },
    {
      "paymentAmount": 2058.33,
      "paymentReference": "payment reference id",
      "paymentMethod": "payment method",
      "paymentDate": "2024-12-04"
      "dateProcessed": "2024-12-08"
    },
    {
      "paymentAmount": 200.00,
      "paymentReference": "payment reference id",
      "paymentMethod": "payment method",
      "paymentDate": "2023-12-04"
      "dateProcessed": "2023-12-08"
    }
  ]
}"""

  private val validHipJsonResponse2024: String = """{
  "balanceDetails": {
    "totalOverdueBalance": 500.00,
    "totalPayableBalance": 500.00,
    "payableDueDate": "2025-04-31",
    "totalPendingBalance": 1500.00,
    "pendingDueDate": "2025-07-15",
    "totalBalance": 2000.00,
    "totalCodedOut": 250.00,
    "totalCreditAvailable": 0.00
  },
  "chargeDetails": [
    {
      "chargeId": "AB1234567",
      "creationDate": "2025-01-15",
      "chargeType": "ITSA",
      "chargeAmount": 1250.00,
      "outstandingAmount": 500.00,
      "taxYear": "2024-2025",
      "dueDate": "2025-04-31",
      "amendments": [
        {
          "amendmentDate": "2025-04-15",
          "amendmentAmount": 500.00,
          "amendmentReason": "money"
        }
      ],
      "codedOutDetail": [
        {
          "amount": 250.00,
          "effectiveDate": "2025-04-01",
          "taxYear": "2024-2025",
          "effectiveTaxYear": "2025-2026"
        }
      ]
    },
    {
      "chargeId": "KL3456789",
      "creationDate": "2025-05-22",
      "chargeType": "VATC",
      "chargeAmount": 1500.00,
      "outstandingAmount": 1500.00,
      "taxYear": "2025-2026",
      "dueDate": "2025-07-15"
    }
  ],
  "refundDetails": [],
  "paymentHistoryDetails": [
    {
      "paymentAmount": 500.00,
      "paymentReference": "payment reference id",
      "paymentMethod": "payment method",
      "paymentDate": "2025-04-11"
      "dateProcessed": "2025-04-15",
      "allocationReference": "allocation reference"
    },
    {
      "paymentAmount": 2058.33,
      "paymentReference": "payment reference id",
      "paymentMethod": "payment method",
      "paymentDate": "2024-12-04"
      "dateProcessed": "2024-12-08"
    }
  ]
}"""

  private val validHipJsonResponse2025: String = """{
  "balanceDetails": {
    "totalOverdueBalance": 500.00,
    "totalPayableBalance": 500.00,
    "payableDueDate": "2025-04-31",
    "totalPendingBalance": 1500.00,
    "pendingDueDate": "2025-07-15",
    "totalBalance": 2000.00,
    "totalCodedOut": 250.00,
    "totalCreditAvailable": 0.00
  },
  "chargeDetails": [],
  "refundDetails": [],
  "paymentHistoryDetails": []
}"""

  "GET /" should {
    "return 200 with details from 2023 for any valid UTR and any other dateFrom" in {
      val result =
        controller.getSelfAssessmentData(validUtr, validDateFrom)(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(validHipJsonResponse2023)
    }

    "return 200 with details from 2024 for any valid UTR and dateFrom 2024" in {
      val result =
        controller.getSelfAssessmentData(validUtr, "2024-04-06")(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(validHipJsonResponse2024)
    }

    "return 200 with details from 2025 for any valid UTR and dateFrom 2025" in {
      val result =
        controller.getSelfAssessmentData(validUtr, "2025-04-06")(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(validHipJsonResponse2025)
    }

    "return 400 BAD_REQUEST with correct error message for invalid correlation ID" in {
      val result =
        controller.getSelfAssessmentData("0111111400", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Submission has not passed validation. Invalid Correlation Id."
      )
    }

    "return 401 UNAUTHORIZED with correct error message for invalid authentication credentials" in {
      val result =
        controller.getSelfAssessmentData("0111111401", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.UNAUTHORIZED
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid basic authentication credentials."
      )
    }

    "return 403 FORBIDDEN with correct error message when authority is denied" in {
      val result =
        controller.getSelfAssessmentData("0111111403", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.FORBIDDEN
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "User does not have authority to retrieve requested record."
      )
    }

    "return 404 NOT_FOUND with correct error message when UTR is not found" in {
      val result =
        controller.getSelfAssessmentData("0111111404", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Identifier not found."
      )
    }

    "return 422 UNPROCESSABLE_ENTITY with correct error message for invalid UTR" in {
      val result =
        controller.getSelfAssessmentData("0111111422", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.UNPROCESSABLE_ENTITY
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Invalid utr entered."
      )
    }

    "return 500 INTERNAL_SERVER_ERROR with correct error message for general internal server errors" in {
      val result =
        controller.getSelfAssessmentData("0111111500", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj("message" -> "Internal server error.")
    }

    "return 502 BAD_GATEWAY with correct error message for service communication errors" in {
      val result =
        controller.getSelfAssessmentData("0111111502", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.BAD_GATEWAY
      contentAsJson(result) shouldBe Json.obj(
        "message" -> "Error communicating with external service."
      )
    }

    "return 503 SERVICE_UNAVAILABLE with correct error message when service unavailable" in {
      val result =
        controller.getSelfAssessmentData("0111111503", validDateFrom)(
          fakeRequest
        )
      status(result) shouldBe Status.SERVICE_UNAVAILABLE
      contentAsJson(result) shouldBe Json.obj("message" -> "Service unavailable")
    }
  }
}
