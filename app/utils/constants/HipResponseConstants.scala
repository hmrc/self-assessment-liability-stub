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

package utils.constants

import models.{BalanceDetails, HipResponse, PaymentHistoryDetails}
import play.api.libs.json.{JsValue, Json}
import utils.ResponseGenerator.today

import java.time.LocalDate
import scala.util.Random

object HipResponseConstants {
  val hipResponseNoPaymentReference: HipResponse = HipResponse(
    balanceDetails = BalanceDetails(
      totalOverdueBalance = 0,
      totalPayableBalance = 100,
      earliestPayableDueDate = Some(today().plusDays(Random.nextInt(30))),
      totalPendingBalance = 100,
      earliestPendingDueDate = Some(today().plusDays(31 + Random.nextInt(150))),
      totalBalance = 200,
      totalCreditAvailable = 0,
      codedOutDetail = List.empty
    ),
    chargeDetails = List.empty,
    refundDetails = List.empty,
    paymentHistoryDetails = List(
      PaymentHistoryDetails(
        paymentAmount = 500.00,
        paymentReference = None,
        paymentMethod = Some("bank transfer"),
        paymentDate = LocalDate.of(2023, 2, 15),
        processedDate = Some(LocalDate.of(2023, 2, 16)),
        allocationReference = Some("charge-123")
      )
    )
  )

  val hipResponseMisingTotalBalance: JsValue = Json.obj(
    "balanceDetails" -> Json.obj(
      "totalOverdueBalance" -> 0,
      "totalPayableBalance" -> 0,
      "totalPendingBalance" -> 0,
      "totalCreditAvailable" -> 0
    )
  )

  val hipResponseMissingChargeId: JsValue = Json.obj(
    "balanceDetails" -> Json.obj(
      "totalOverdueBalance" -> 0,
      "totalPayableBalance" -> 0,
      "totalPendingBalance" -> 0,
      "totalCreditAvailable" -> 0
    ),
    "chargeDetails" -> Json.arr(
      Json.obj(
        "creationDate" -> "2025-12-01",
        "chargeType" -> "IncomeTax",
        "chargeAmount" -> 125.00,
        "outstandingAmount" -> 25.00,
        "taxYear" -> "2024-25",
        "dueDate" -> "2026-01-31"
      )
    )
  )
  val hipResponseOnlyBalanceDetails: JsValue = Json.obj(
    "balanceDetails" -> Json.obj(
      "totalOverdueBalance" -> 0,
      "totalPayableBalance" -> 0,
      "totalPendingBalance" -> 0,
      "totalBalance" -> 0,
      "totalCreditAvailable" -> 0
    )
  )

  val hipResponseMissingDates: HipResponse = HipResponse(
    balanceDetails = BalanceDetails(
      totalOverdueBalance = 0,
      totalPayableBalance = 100,
      earliestPayableDueDate = None,
      totalPendingBalance = 100,
      earliestPendingDueDate = None,
      totalBalance = 200,
      totalCreditAvailable = 0,
      codedOutDetail = List.empty
    ),
    chargeDetails = List.empty,
    refundDetails = List.empty,
    paymentHistoryDetails = List.empty
  )

}
