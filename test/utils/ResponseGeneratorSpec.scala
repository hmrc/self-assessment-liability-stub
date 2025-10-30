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

import models.{Amendment, ChargeDetails, HipResponse, PaymentHistoryDetails}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class ResponseGeneratorSpec extends AnyWordSpec with Matchers {
  val fromDate: LocalDate = LocalDate.of(2023, 1, 1)
  val toDate: LocalDate = LocalDate.of(2023, 12, 31)
  val today: LocalDate = LocalDate.now()
  "ResponseGenerator" should {
    "generate a response from given date range" in {

      val hipResponse: HipResponse = ResponseGenerator.generateResponse(fromDate, toDate)

      hipResponse.chargeDetails should not be empty
      hipResponse.paymentHistoryDetails should not be empty
      hipResponse.paymentHistoryDetails.size should be <= 3
      hipResponse.chargeDetails.size should be <= 3
      hipResponse.chargeDetails.map { charge =>
        charge.creationDate.getYear should (be >= 2023 and be <= 2024)
        hipResponse.paymentHistoryDetails.foreach { payments =>
          payments.paymentDate.getYear should (be >= 2023 and be <= 2024)
        }
      }
    }

    "generate valid charge based on interest accrued" in {

      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails

      charges.foreach { charge =>

        charge.chargeId should not be empty
        charge.chargeId should fullyMatch regex "^[A-Za-z0-9-]{1,18}$"
        List("ITSA", "Penalty", "PAYE", "POA") should contain(charge.chargeType)
        charge.chargeAmount should be > charge.outstandingAmount
        charge.taxYear should fullyMatch regex "[0-9]{4}"

        val interestFields = List(
          charge.accruingInterest,
          charge.accruingInterestRate,
          charge.accruingInterestPeriod,
          charge.outstandingInterestDue
        )

        interestFields.count(_.isDefined) should (be(0) or be(4))
      }
    }

    "generate valid payments" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val payments = response.paymentHistoryDetails

      payments should not be empty
      payments.foreach { payment =>
        payment.paymentAmount should (be >= BigDecimal(500.00) and be <= BigDecimal(50000.00))
        payment.paymentReference should not be empty
        payment.paymentMethod shouldBe defined
        List("bank transfer", "card", "direct debit", "cheque") should contain(
          payment.paymentMethod.get
        )

        payment.processedDate shouldBe defined
        payment.allocationReference should not be empty
      }
    }

    "generate valid refunds" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val refunds = response.refundDetails

      refunds.foreach { refund =>

        refund.refundMethod shouldBe defined
        refund.refundRequestDate shouldBe defined
        refund.refundRequestAmount should be > BigDecimal(0.0)
        refund.refundDescription shouldBe defined
        refund.interestAddedToRefund shouldBe defined
        refund.interestAddedToRefund.get should be >= BigDecimal(0.0)
        refund.totalRefundAmount should be >= refund.refundRequestAmount
        refund.refundStatus shouldBe defined
      }
    }

    "balance details calculations should properly reflect each charge type" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val balanceDetails = response.balanceDetails

      balanceDetails.totalOverdueBalance should be >= BigDecimal(0.0)
      balanceDetails.totalPayableBalance should be >= BigDecimal(0.0)
      balanceDetails.totalPendingBalance should be >= BigDecimal(0.0)
      balanceDetails.totalBalance should be >= BigDecimal(0.0)
      balanceDetails.totalCreditAvailable should be >= BigDecimal(0.0)

      if (balanceDetails.totalPayableBalance > 0) {
        balanceDetails.earliestPayableDueDate shouldBe defined
      }
      if (balanceDetails.totalPendingBalance > 0) {
        balanceDetails.earliestPendingDueDate shouldBe defined
      }
    }

    "generate refund if total payment is more than total charge amounts" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails
      val payments = response.paymentHistoryDetails
      val refunds = response.refundDetails

      val chargeIds = charges.map(_.chargeId)
      payments.foreach { payment =>
        payment.allocationReference.foreach { ref =>
          chargeIds should contain(ref)
        }
      }

      val totalPayments = payments.map(_.paymentAmount).sum
      val totalCharges = charges.map(_.chargeAmount).sum

      if (totalPayments > totalCharges) {
        refunds should not be empty
      }
    }

    "create tax year correctly" in {
      val fromDate = LocalDate.of(2022, 1, 1)
      val toDate = LocalDate.of(2024, 12, 31)
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails
      val payments = response.paymentHistoryDetails

      val chargeYears = charges.map(_.creationDate.getYear)
      chargeYears should contain allOf (2022, 2023, 2024)
      charges.foreach { charge =>
        val correspondingPayment = payments.filter(_.allocationReference.contains(charge.chargeId))

        correspondingPayment should not be empty
      }
    }

    "create a coded amount for the tax year of a overdue charge" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val charges = response.chargeDetails
      val balanceDetails = response.balanceDetails
      val overdueCharges = charges.filter(_.dueDate.isBefore(today))
      val codedOutDetail = response.balanceDetails.codedOutDetail
      val overdueChargesWithAnOutstanding = overdueCharges.filter(_.outstandingAmount > 0)
      codedOutDetail.size should be <= overdueCharges.size
      overdueCharges.map(_.outstandingAmount).sum should be >= codedOutDetail.map(_.totalAmount).sum
      if (overdueChargesWithAnOutstanding.nonEmpty) {
        balanceDetails.codedOutDetail should not be empty
        val codedOutDetail = balanceDetails.codedOutDetail.head

        codedOutDetail.totalAmount should be > BigDecimal(0.0)
        codedOutDetail.effectiveEndDate should be > codedOutDetail.effectiveStartDate

        val expectedOverdueBalance =
          overdueChargesWithAnOutstanding.map(_.outstandingAmount).sum - codedOutDetail.totalAmount
        balanceDetails.totalOverdueBalance shouldBe expectedOverdueBalance
      }
    }

    "apply partial credit when credit amount is less than outstanding charge amount" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val chargesWithCreditAmendments = response.chargeDetails.filter(
        _.amendments.exists(_.amendmentReason == "Credit applied from overpayment")
      )

      chargesWithCreditAmendments.foreach { charge =>
        val creditAmendments =
          charge.amendments.filter(_.amendmentReason == "Credit applied from overpayment")
        creditAmendments.foreach { amendment =>
          amendment.amendmentAmount should be <= charge.chargeAmount
        }
      }
    }

    "return original charges and zero credit when total credit is negative" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val totalPayments = response.paymentHistoryDetails.map(_.paymentAmount).sum
      val totalCharges = response.chargeDetails.map(_.chargeAmount).sum
      val totalRefunds = response.refundDetails.map(_.refundRequestAmount).sum
      val creditAmendments = response.chargeDetails
        .flatMap(_.amendments)
        .filter(_.amendmentReason == "Credit applied from overpayment")
      if (totalPayments <= (totalCharges + totalRefunds)) {
        response.balanceDetails.totalCreditAvailable shouldBe BigDecimal(0)
        creditAmendments shouldBe empty
      } else {
        creditAmendments should not be empty
        creditAmendments.map(_.amendmentDate should (be <= today))
      }
    }
    "calculate balance details correctly based on different charges" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val balanceDetails = response.balanceDetails
      val charges = response.chargeDetails

      val payableCharges = charges.filter { charge =>
        charge.dueDate.isBefore(today.plusDays(30)) && charge.dueDate.isAfter(today)
      }

      val pendingCharges = charges.filter(_.dueDate.isAfter(today.plusDays(30)))

      val expectedTotalPayableBalance = payableCharges.map(_.outstandingAmount).sum
      val expectedTotalPendingBalance = pendingCharges.map(_.outstandingAmount).sum

      balanceDetails.totalPayableBalance shouldBe expectedTotalPayableBalance
      balanceDetails.totalPendingBalance shouldBe expectedTotalPendingBalance

      if (payableCharges.nonEmpty && payableCharges.map(_.outstandingAmount).sum > 0) {
        balanceDetails.earliestPayableDueDate shouldBe Some(payableCharges.map(_.dueDate).min)
      } else {
        balanceDetails.earliestPayableDueDate shouldBe None
      }

      if (pendingCharges.nonEmpty && pendingCharges.map(_.outstandingAmount).sum > 0) {
        balanceDetails.earliestPendingDueDate shouldBe Some(pendingCharges.map(_.dueDate).min)
      } else {
        balanceDetails.earliestPendingDueDate shouldBe None
      }
    }
    "generate a response from current year provided" in {
      val hipResponse: HipResponse = ResponseGenerator.generateResponse(today.minusDays(30), today)

      hipResponse.chargeDetails should not be empty
      hipResponse.paymentHistoryDetails should not be empty
      hipResponse.chargeDetails.map { charge =>
        charge.creationDate.getYear should (be >= 2025 and be <= 2026)
        hipResponse.paymentHistoryDetails.foreach { payments =>
          payments.paymentDate.getYear should (be >= 2025 and be <= 2026)
        }
      }
    }

    "generate a response from future date provided" in {
      val hipResponse: HipResponse = ResponseGenerator.generateResponse(today, today.plusDays(30))

      hipResponse.chargeDetails should not be empty
      hipResponse.paymentHistoryDetails should not be empty
      hipResponse.chargeDetails.map { charge =>
        charge.creationDate.getYear should (be >= 2025 and be <= 2026)
        hipResponse.paymentHistoryDetails.foreach { payments =>
          payments.paymentDate.getYear should (be >= 2025 and be <= 2026)
        }
      }
    }
  }

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
        creditUtils.allocateCredit(BigDecimal(500.00), List.empty)
      emptyResult shouldBe empty
      remainingCredit1 shouldBe BigDecimal(500.00)

      val singleCharge =
        overdueCharge.copy(chargeId = "EFG23456", outstandingAmount = BigDecimal(300.00))

      val (partialResult, remainingCredit3) =
        creditUtils.allocateCredit(BigDecimal(200.00), List(singleCharge))
      val partialCharge = partialResult.head
      partialCharge.outstandingAmount shouldBe BigDecimal(100.00)
      partialCharge.amendments should have size 1
      partialCharge.amendments.head.amendmentAmount shouldBe BigDecimal(200.00)
      remainingCredit3 shouldBe BigDecimal(0.00)

      val freshCharge =
        singleCharge.copy(chargeId = "HIJ45678", outstandingAmount = BigDecimal(300.00))
      val (excessResult, remainingCredit4) =
        creditUtils.allocateCredit(BigDecimal(500.00), List(freshCharge, singleCharge))
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
        creditUtils.allocateCredit(BigDecimal(100.00), List(overdueZeroOutstanding, futureEligible))

      futureAllocRemaining shouldBe BigDecimal(0.00)
      val updatedFuture = futureAllocResult.find(_.chargeId == "NBC12345").get
      updatedFuture.outstandingAmount shouldBe BigDecimal(50.00)
      updatedFuture.amendments should have size 1
      updatedFuture.amendments.head.amendmentAmount shouldBe BigDecimal(100.00)

      futureAllocResult.find(_.chargeId == "ZBC12345").get.outstandingAmount shouldBe BigDecimal(
        0.00
      )

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
          amendments = Nil
        )
      )

      val payments = paymentUtils.generatePaymentHistory(today.minusDays(10))
      val paymentWithoutProcessed = List(payments.copy(paymentAmount = 100))

      val refunds = Nil

      val (updatedCharges, remainingCredit) = creditUtils.allocateCreditToOutstandingCharges(
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
          amendments = Nil
        )
      )

      val payments = paymentUtils.generatePaymentHistory(today.minusDays(10))
      val paymentWithoutProcessed = List(payments.copy(paymentAmount = 200))

      val refunds = Nil

      val (updatedCharges, remainingCredit) = creditUtils.allocateCreditToOutstandingCharges(
        charges,
        paymentWithoutProcessed,
        refunds
      )
      val (allocatedCharges, creditsAllocated) =
        creditUtils.allocateCredit(remainingCredit, updatedCharges)

      updatedCharges shouldBe allocatedCharges
    }
  }

  "generateCharge method" should {

    "return an empty list when there are no payments" in {
      val result = chargesUtils.generateCharge(Nil)
      result shouldBe Nil
    }
    "generate a charge for a single payment" in {
      val payments = paymentUtils.generatePaymentHistory(today.minusDays(10))

      val result = chargesUtils.generateCharge(List(payments))
      result.size shouldBe 1
      val charge = result.head

      charge.creationDate.isAfter(payments.paymentDate.minusDays(16)) shouldBe true
      charge.creationDate.isBefore(payments.paymentDate.minusDays(9)) shouldBe true

      charge.outstandingAmount shouldEqual charge.chargeAmount

      charge.chargeAmount shouldBe >=(BigDecimal(0))
      charge.outstandingAmount shouldBe >=(BigDecimal(0))
    }
    "generate a charge for multiple payments" in {

      val payments = List(
        paymentUtils.generatePaymentHistory(today.minusDays(5)),
        paymentUtils.generatePaymentHistory(today.minusDays(50)),
        paymentUtils.generatePaymentHistory(today.minusDays(1))
      )

      val result = chargesUtils.generateCharge(payments)
      result.size shouldBe payments.size
    }
    "apply not recent statement logic where paymentDate before today - 45" in {
      val payments =
        paymentUtils.generatePaymentHistory(today.minusDays(46))

      val result = chargesUtils.generateCharge(List(payments))
      val charge = result.head

      val amendmentAmount =
        if (charge.chargeAmount < payments.paymentAmount) charge.chargeAmount
        else payments.paymentAmount

      charge.outstandingAmount shouldBe >=(BigDecimal(0))
      charge.outstandingAmount shouldBe <=(charge.chargeAmount)

      if (charge.chargeAmount > 0 && amendmentAmount > 0) {
        charge.outstandingAmount shouldBe <(charge.chargeAmount)
      }
    }
    "treat paymentDate exactly today - 45 as recent (edge case)" in {
      val payments = paymentUtils.generatePaymentHistory(today.minusDays(45))

      val result = chargesUtils.generateCharge(List(payments))
      val charge = result.head

      charge.outstandingAmount shouldEqual charge.chargeAmount
    }

    "use paymentDate + 6 when processedDate is missing" in {
      val payments = paymentUtils.generatePaymentHistory(today.minusDays(46))
      val paymentWithoutProcessed = payments.copy(processedDate = None)

      val result = chargesUtils.generateCharge(List(paymentWithoutProcessed))
      val charge = result.head

      charge.amendments.nonEmpty shouldBe true

      val amendDate =
        charge.amendments.headOption
          .map(_.amendmentDate)
          .getOrElse(fail("Expected at least one amendment"))

      amendDate shouldEqual paymentWithoutProcessed.paymentDate.plusDays(6)
    }
    "generate chargeId randomly when allocationReference is empty" in {
      val payments = paymentUtils.generatePaymentHistory(today.minusDays(10))
      val paymentWithoutRef = payments.copy(allocationReference = Nil)

      val result = chargesUtils.generateCharge(List(paymentWithoutRef))
      val charge = result.head

      charge.chargeId should not be empty
    }
  }
  "calculateInterestOrGenerateRefund method" should {
    "return empty list when both inputs are empty" in {
      val result = refundUtils.calculateInterestOrGenerateRefund(
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
        refundUtils.calculateInterestOrGenerateRefund(charges, payments)

      refunds.isEmpty shouldBe true
      updatedCharges.size shouldBe charges.size
      updatedCharges.map(_.chargeId).toSet shouldBe charges.map(_.chargeId).toSet
    }
  }
}
