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

package utils

import models.{ChargeDetails, PaymentHistoryDetails}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import utils.ResponseGenerator.today

import java.time.LocalDate

class RefundUtilsSpec extends AnyWordSpec with Matchers {
  "calculateInterestOrGenerateRefund method" should {
    "return empty list when both inputs are empty" in {
      val result = RefundUtils.calculateInterestOrGenerateRefund(
        charges = List.empty[ChargeDetails],
        payments = List.empty[PaymentHistoryDetails]
      )

      result._1.isEmpty shouldBe true
      result._2.isEmpty shouldBe true
    }
    "apply interest and produce no refunds when payments are less then charges in a year" in {
      val charges = List(
        ChargeDetails(
          chargeId = "ABC12345",
          creationDate = today.minusMonths(6),
          chargeType = "ITSA",
          chargeAmount = BigDecimal(200.00),
          taxYear = "2023-2024",
          dueDate = today.minusMonths(3),
          amendments = List.empty,
          outstandingAmount = BigDecimal(1000.00),
          outstandingInterestDue = None,
          accruingInterest = None,
          accruingInterestPeriod = None,
          accruingInterestRate = None
        )
      )

      val payments = List(
        PaymentHistoryDetails(
          paymentAmount = BigDecimal(100.00),
          paymentReference = "payment-123",
          paymentMethod = Some("bank transfer"),
          paymentDate = today.minusMonths(6),
          processedDate = Some(today.minusMonths(6).plusDays(6)),
          allocationReference = List("charge-123")
        )
      )

      val (updatedCharges, refunds) =
        RefundUtils.calculateInterestOrGenerateRefund(charges, payments)

      refunds.isEmpty shouldBe true
      updatedCharges.size shouldBe charges.size
      updatedCharges.map(_.chargeId).toSet shouldBe charges.map(_.chargeId).toSet
    }
  }
}
