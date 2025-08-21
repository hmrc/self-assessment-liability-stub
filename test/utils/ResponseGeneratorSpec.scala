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

import models.HipResponse
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
          payments.paymentDate should be >= charge.creationDate
        }
      }
    }

    "generate valid charge based on statement date" in {

      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails

      charges.foreach { charge =>

        charge.chargeId should not be empty
        charge.creationDate should not be null
        List("ITSA", "Penalty", "PAYE", "POA") should contain(charge.chargeType)
        charge.chargeAmount should be > charge.outstandingAmount
        charge.taxYear should fullyMatch regex "[0-9]{4}-[0-9]{4}"
        charge.dueDate should not be null

        if (charge.outstandingAmount > 0 & charge.dueDate.isBefore(today)) {
          charge.outstandingInterestDue shouldBe defined
          charge.accruingInterest shouldBe defined
          charge.accruingInterestPeriod shouldBe defined
          charge.accruingInterestRate shouldBe Some(0.05)
        } else {
          charge.outstandingInterestDue shouldBe None
          charge.accruingInterest shouldBe None
          charge.accruingInterestPeriod shouldBe None
          charge.accruingInterestRate shouldBe None
        }

        val isRecentStatement = charge.creationDate.isAfter(LocalDate.now().minusDays(45))
        if (!isRecentStatement) {
          charge.amendments should not be empty
          charge.amendments.foreach { amendment =>
            amendment.amendmentDate should not be null
            amendment.amendmentAmount should be >= BigDecimal(0.0)
            amendment.amendmentReason should not be null
            amendment.paymentMethod shouldBe defined
            List("bank transfer", "card", "direct debit", "cheque", "Credit") should contain(
              amendment.paymentMethod.get
            )
            amendment.paymentDate shouldBe defined
          }
        }
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

      val chargeYears = charges.map(_.creationDate.getYear)
      chargeYears should contain allOf (2022, 2023, 2024)

      charges.foreach { charge =>
        val year = charge.creationDate.getYear
        charge.taxYear shouldBe s"$year-${year + 1}"
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
}
