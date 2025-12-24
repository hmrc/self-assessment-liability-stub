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
  val charges =
    ChargeDetails(
      chargeId = "ABC12345",
      creationDate = today().minusMonths(6),
      chargeType = "ITSA",
      chargeAmount = BigDecimal(200.00),
      taxYear = "2023-2024",
      dueDate = today().minusMonths(3),
      amendments = List.empty,
      outstandingAmount = BigDecimal(1000.00),
      outstandingInterestDue = None,
      accruingInterest = None,
      accruingInterestPeriod = None,
      accruingInterestRate = None
    )

  val payments =
    PaymentHistoryDetails(
      paymentAmount = BigDecimal(100.00),
      paymentReference = Some("payment-123"),
      paymentMethod = Some("bank transfer"),
      paymentDate = today().minusMonths(6),
      processedDate = Some(today().minusMonths(6).plusDays(6)),
      allocationReference = Some("charge-123")
    )
  "calculateInterestOrGenerateRefund method" should {
    "apply interest to charges when outstanding amount is negative" in {
      val (updatedCharges, refunds) =
        RefundUtils.calculateInterestOrGenerateRefund(List(charges), List(payments))

      refunds.isEmpty shouldBe true
      updatedCharges.size shouldBe List(charges).size
      updatedCharges.map(_.chargeId) shouldBe List(charges).map(_.chargeId)
    }

    "return unchanged charges when outstanding amount is zero" in {
      val chargesOutstandingZero = List(charges.copy(chargeAmount = BigDecimal(150)))

      val paymentsOutstandingZero = List(payments.copy(paymentAmount = BigDecimal(150)))

      val (updatedCharges, refunds) =
        RefundUtils.calculateInterestOrGenerateRefund(
          chargesOutstandingZero,
          paymentsOutstandingZero
        )

      refunds shouldBe empty
      updatedCharges shouldEqual chargesOutstandingZero
    }

    "generate refund when outstanding amount is positive" in {
      val chargesPositiveOutstanding = List(charges.copy(chargeAmount = BigDecimal(100)))

      val paymentsPositiveOutstanding = List(payments.copy(paymentAmount = BigDecimal(250)))

      val (updatedCharges, refunds) =
        RefundUtils.calculateInterestOrGenerateRefund(
          chargesPositiveOutstanding,
          paymentsPositiveOutstanding
        )

      updatedCharges shouldEqual chargesPositiveOutstanding
      refunds should have size 1
      refunds.head.refundRequestAmount shouldBe BigDecimal(150.00)
    }
  }
}
