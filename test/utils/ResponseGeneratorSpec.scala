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

import models.{Amendment, ChargeDetails, HipResponse}
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

      hipResponse.balanceDetails should not be null
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
        charge.creationDate should not be null
        List("ITSA", "Penalty", "PAYE", "POA") should contain(charge.chargeType)
        charge.chargeAmount should be > charge.outstandingAmount
        charge.taxYear should fullyMatch regex "[0-9]{4}-[0-9]{4}"
        charge.dueDate should not be null

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
        payment.paymentDate should not be null
        payment.processedDate shouldBe defined
        payment.allocationReference should not be empty
      }
    }

    "generate valid refunds" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val refunds = response.refundDetails

      refunds.foreach { refund =>
        refund.refundDate should not be null
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
        val expectedTaxYear = ResponseGenerator.getTaxYear(correspondingPayment.head.paymentDate)
        charge.taxYear shouldBe expectedTaxYear
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
        codedOutDetail.effectiveStartDate should not be null
        codedOutDetail.effectiveEndDate should not be null
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
        val creditAmendments = charge.amendments.filter(_.amendmentReason == "Credit applied from overpayment")
        creditAmendments.foreach { amendment =>
          amendment.amendmentAmount should be <= charge.chargeAmount
          amendment.updatedChargeAmount shouldBe defined
          amendment.updatedChargeAmount.get shouldBe (charge.chargeAmount - amendment.amendmentAmount)
        }
      }
    }

    "return original charges and zero credit when total credit is negative" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val totalPayments = response.paymentHistoryDetails.map(_.paymentAmount).sum
      val totalCharges = response.chargeDetails.map(_.chargeAmount).sum
      val totalRefunds = response.refundDetails.map(_.refundRequestAmount).sum
      val creditAmendments = response.chargeDetails.flatMap(_.amendments)
        .filter(_.amendmentReason == "Credit applied from overpayment")
      if (totalPayments <= (totalCharges + totalRefunds)) {
        response.balanceDetails.totalCreditAvailable shouldBe BigDecimal(0)
        creditAmendments shouldBe empty
      }
      else {
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
  }
  "allocateCredit method" should {
    "handle comprehensive credit allocation scenarios" in {
      val baseCharge = ChargeDetails(
        chargeId = "12345",
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

      val (emptyResult, remainingCredit1) = ResponseGenerator.allocateCredit(BigDecimal(500.00), List.empty)
      emptyResult shouldBe empty
      remainingCredit1 shouldBe BigDecimal(500.00)

      val zeroOutstandingCharge = baseCharge.copy(chargeId = "zero1", outstandingAmount = BigDecimal(0.00))
      val (zeroResult, remainingCredit2) = ResponseGenerator.allocateCredit(BigDecimal(500.00), List(zeroOutstandingCharge))
      zeroResult should contain only zeroOutstandingCharge
      remainingCredit2 shouldBe BigDecimal(500.00)

      val singleCharge = baseCharge.copy(chargeId = "single1", outstandingAmount = BigDecimal(300.00))


      val (partialResult, remainingCredit3) = ResponseGenerator.allocateCredit(BigDecimal(200.00), List(singleCharge))
      val partialCharge = partialResult.head
      partialCharge.outstandingAmount shouldBe BigDecimal(100.00)
      partialCharge.amendments should have size 1
      partialCharge.amendments.head.amendmentAmount shouldBe BigDecimal(200.00)
      remainingCredit3 shouldBe BigDecimal(0.00)

      val freshCharge = singleCharge.copy(amendments = List.empty, outstandingAmount = BigDecimal(300.00))
      val (excessResult, remainingCredit4) = ResponseGenerator.allocateCredit(BigDecimal(500.00), List(freshCharge))
      val excessCharge = excessResult.head
      excessCharge.outstandingAmount shouldBe BigDecimal(0.00)
      excessCharge.amendments.head.amendmentAmount shouldBe BigDecimal(300.00)
      remainingCredit4 shouldBe BigDecimal(200.00)


      val existingAmendment = Amendment(
        amendmentDate = today.minusDays(10),
        amendmentAmount = BigDecimal(200.00),
        amendmentReason = "payment",
        updatedChargeAmount = Some(BigDecimal(800.00)),
        paymentMethod = Some("card"),
        paymentDate = Some(today.minusDays(10))
      )
      val chargeWithAmendments = baseCharge.copy(
        chargeId = "withAmendments1",
        amendments = List(existingAmendment),
        outstandingAmount = BigDecimal(800.00)
      )

      val (amendmentResult, remainingCredit6) = ResponseGenerator.allocateCredit(BigDecimal(500.00), List(chargeWithAmendments))
      val updatedAmendmentCharge = amendmentResult.head
      updatedAmendmentCharge.outstandingAmount shouldBe BigDecimal(300.00)
      updatedAmendmentCharge.amendments should have size 2

      val creditAmendment = updatedAmendmentCharge.amendments.find(_.amendmentReason == "Credit applied from overpayment").get
      creditAmendment.amendmentAmount shouldBe BigDecimal(500.00)
      remainingCredit6 shouldBe BigDecimal(0.00)
    }
  }

}
