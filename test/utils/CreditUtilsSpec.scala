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

import models.{Amendment, ChargeDetails}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class CreditUtilsSpec extends AnyWordSpec with Matchers {
  val fromDate: LocalDate = LocalDate.of(2023, 1, 1)
  val toDate: LocalDate = LocalDate.of(2023, 12, 31)
  val today: LocalDate = LocalDate.now()

  "allocateCredit method" should {
    "handle comprehensive credit allocation scenarios" in {
      val overdueCharge = ChargeDetails(
        chargeId = "ABC12345",
        creationDate = today.minusMonths(6),
        chargeType = "ITSA",
        chargeAmount = BigDecimal(1000.00),
        taxYear = "2023-2024",
        dueDate = today.minusMonths(3),
        amendments = List.empty,
        outstandingAmount = BigDecimal(1000.00),
        outstandingInterestDue = None,
        accruingInterest = None,
        accruingInterestPeriod = None,
        accruingInterestRate = None
      )

      val (emptyResult, remainingCredit1) =
        CreditUtils.allocateCredit(BigDecimal(500.00), List.empty)
      emptyResult shouldBe empty
      remainingCredit1 shouldBe BigDecimal(500.00)

      val singleCharge =
        overdueCharge.copy(chargeId = "EFG23456", outstandingAmount = BigDecimal(300.00))

      val (partialResult, remainingCredit3) =
        CreditUtils.allocateCredit(BigDecimal(200.00), List(singleCharge))
      val partialCharge = partialResult.head
      partialCharge.outstandingAmount shouldBe BigDecimal(100.00)
      partialCharge.amendments should have size 1
      partialCharge.amendments.head.amendmentAmount shouldBe BigDecimal(200.00)
      remainingCredit3 shouldBe BigDecimal(0.00)

      val freshCharge =
        singleCharge.copy(chargeId = "HIJ45678", outstandingAmount = BigDecimal(300.00))
      val (excessResult, remainingCredit4) =
        CreditUtils.allocateCredit(BigDecimal(500.00), List(freshCharge, singleCharge))
      excessResult.map(_.outstandingAmount).sum shouldBe BigDecimal(100.00)
      excessResult.map(_.amendments.map(_.amendmentAmount).sum).sum shouldBe BigDecimal(500.00)
      remainingCredit4 shouldBe BigDecimal(0.00)
      excessResult.size shouldBe 2
      excessResult
        .map(_.amendments.map(_.amendmentReason).contains("Credit applied from overpayment"))
        .size shouldBe 2

      val overdueZeroOutstanding = overdueCharge.copy(
        chargeId = "ZBC12345",
        dueDate = today.minusDays(10),
        outstandingAmount = BigDecimal(0.00),
        amendments = List.empty
      )

      val futureEligible = overdueCharge.copy(
        chargeId = "NBC12345",
        dueDate = today.plusDays(10),
        outstandingAmount = BigDecimal(150.00),
        amendments = List.empty
      )

      val (futureAllocResult, futureAllocRemaining) =
        CreditUtils.allocateCredit(BigDecimal(100.00), List(overdueZeroOutstanding, futureEligible))

      futureAllocRemaining shouldBe BigDecimal(0.00)
      val updatedFuture = futureAllocResult.find(_.chargeId == "NBC12345").get
      updatedFuture.outstandingAmount shouldBe BigDecimal(50.00)
      updatedFuture.amendments should have size 1
      updatedFuture.amendments.head.amendmentAmount shouldBe BigDecimal(100.00)

      futureAllocResult.find(_.chargeId == "ZBC12345").get.outstandingAmount shouldBe BigDecimal(
        0.00
      )

    }

    "recursively allocate credit from overdue to future charges" in {
      val overdueCharge = ChargeDetails(
        chargeId = "ABC12345",
        creationDate = today.minusMonths(2),
        chargeType = "ITSA",
        chargeAmount = BigDecimal(50),
        taxYear = "2023-2024",
        dueDate = today.minusDays(15),
        amendments = List.empty,
        outstandingAmount = BigDecimal(50),
        outstandingInterestDue = None,
        accruingInterest = None,
        accruingInterestPeriod = None,
        accruingInterestRate = None
      )

      val futureCharge = ChargeDetails(
        chargeId = "DEF12345",
        creationDate = today.minusMonths(1),
        chargeType = "ITSA",
        chargeAmount = BigDecimal(100),
        taxYear = "2023-2024",
        dueDate = today.plusDays(10),
        amendments = List.empty,
        outstandingAmount = BigDecimal(100),
        outstandingInterestDue = None,
        accruingInterest = None,
        accruingInterestPeriod = None,
        accruingInterestRate = None
      )

      val (result, remaining) =
        CreditUtils.allocateCredit(BigDecimal(120), List(futureCharge, overdueCharge))

      remaining shouldBe BigDecimal(0)

      val updatedOverdue = result.find(_.chargeId == "ABC12345").get
      updatedOverdue.outstandingAmount shouldBe BigDecimal(0)
      updatedOverdue.amendments should have size 1
      updatedOverdue.amendments.head.amendmentAmount shouldBe BigDecimal(50)

      val updatedFuture = result.find(_.chargeId == "DEF12345").get
      updatedFuture.outstandingAmount shouldBe BigDecimal(30)
      updatedFuture.amendments should have size 1
      updatedFuture.amendments.head.amendmentAmount shouldBe BigDecimal(70)
    }
  }

  "allocateCreditToOutstandingCharges method" should {
    "No allocation if the totalCreditAvailable less than 0" in {

      val charges = List(
        ChargeDetails(
          chargeId = "ABC12345",
          creationDate = today.minusDays(10),
          chargeType = "ITSA",
          chargeAmount = BigDecimal(150),
          outstandingAmount = BigDecimal(100),
          taxYear = "2024-25",
          dueDate = today.minusDays(11),
          outstandingInterestDue = None,
          accruingInterest = None,
          accruingInterestPeriod = None,
          accruingInterestRate = None,
          amendments = List.empty
        )
      )

      val payments = PaymentUtils.generatePaymentHistory(today.minusDays(10))
      val paymentWithoutProcessed = List(payments.copy(paymentAmount = 100))

      val refunds = List.empty

      val (updatedCharges, remainingCredit) = CreditUtils.allocateCreditToOutstandingCharges(
        charges,
        paymentWithoutProcessed,
        refunds
      )

      updatedCharges shouldBe charges
    }
    "automatically allocateCredit if the totalCreditAvailable greater than 0" in {
      val charges = List(
        ChargeDetails(
          chargeId = "ABC12345",
          creationDate = today.minusDays(10),
          chargeType = "ITSA",
          chargeAmount = BigDecimal(150),
          outstandingAmount = BigDecimal(100),
          taxYear = "2024-25",
          dueDate = today.minusDays(11),
          outstandingInterestDue = None,
          accruingInterest = None,
          accruingInterestPeriod = None,
          accruingInterestRate = None,
          amendments = List.empty
        )
      )

      val payments = PaymentUtils.generatePaymentHistory(today.minusDays(10))
      val paymentWithoutProcessed = List(payments.copy(paymentAmount = 200))

      val refunds = List.empty

      val (updatedCharges, remainingCredit) = CreditUtils.allocateCreditToOutstandingCharges(
        charges,
        paymentWithoutProcessed,
        refunds
      )
      val (allocatedCharges, creditsAllocated) =
        CreditUtils.allocateCredit(remainingCredit, updatedCharges)

      updatedCharges shouldBe allocatedCharges
    }
  }
}
