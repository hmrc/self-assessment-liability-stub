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
  val today = LocalDate.now()
  "ResponseGenerator" should {
    "generate a response from given date range" in {

      val hipResponse: HipResponse = ResponseGenerator.generateResponse(fromDate, toDate)

      hipResponse.balanceDetails should not be null
      hipResponse.chargeDetails shouldBe defined
      hipResponse.refundDetails shouldBe defined
      hipResponse.paymentHistoryDetails shouldBe defined

      hipResponse.chargeDetails.foreach { charges =>
        charges.size should be <= 3
        charges.foreach { charge =>
          charge.creationDate.getYear should (be >= 2023 and be <= 2024)

          hipResponse.paymentHistoryDetails.foreach { payments =>
            payments.size should be <= 3
            payments.foreach { payment =>
              payment.paymentDate.getYear should (be >= 2023 and be <= 2024)
              payment.paymentDate should be >= charge.creationDate
            }
          }
        }
      }
    }

    "generate response with single year range" in {

      val hipResponse: HipResponse = ResponseGenerator.generateResponse(fromDate, toDate)

      hipResponse.balanceDetails should not be null
      hipResponse.chargeDetails shouldBe defined
      hipResponse.refundDetails shouldBe defined
      hipResponse.paymentHistoryDetails shouldBe defined

      val charges = hipResponse.chargeDetails.get
      val payments = hipResponse.paymentHistoryDetails.get

      charges should not be empty
      payments should not be empty

      charges.foreach { charge =>
        charge.creationDate.getYear shouldBe 2023
        charge.taxYear shouldBe "2023-2024"
      }
    }

    "generate valid charge details structure" in {

      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails.get

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
          charge.amendments shouldBe defined
          charge.amendments.get should not be empty
          charge.amendments.get.foreach { amendment =>
            amendment.amendmentDate should not be null
            amendment.amendmentAmount should be >= 0.0
            amendment.amendmentReason shouldBe defined
            amendment.paymentMethod shouldBe defined
            List("bank transfer", "card", "direct debit", "cheque") should contain(
              amendment.paymentMethod.get
            )
            amendment.paymentDate shouldBe defined
          }
        }
      }
    }

    "generate valid payment history structure" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val payments = response.paymentHistoryDetails.get

      payments should not be empty
      payments.foreach { payment =>
        payment.paymentAmount should (be >= 500.00 and be <= 50000.00)
        payment.paymentReference should not be empty
        payment.paymentMethod shouldBe defined
        List("bank transfer", "card", "direct debit", "cheque") should contain(
          payment.paymentMethod.get
        )
        payment.paymentDate should not be null
        payment.processedDate shouldBe defined
        payment.allocationReference shouldBe defined
      }
    }

    "generate valid refund details structure" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val refunds = response.refundDetails.get

      refunds.foreach { refund =>
        refund.refundDate should not be null
        refund.refundMethod shouldBe defined
        refund.refundRequestDate shouldBe defined
        refund.refundRequestAmount should be > 0.0
        refund.refundDescription shouldBe defined
        refund.interestAddedToRefund shouldBe defined
        refund.interestAddedToRefund.get should be >= 0.0
        refund.totalRefundAmount should be >= refund.refundRequestAmount
        refund.refundStatus shouldBe defined
      }
    }

    "generate valid balance details" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val balanceDetails = response.balanceDetails

      balanceDetails.totalOverdueBalance should be >= 0.0
      balanceDetails.totalPayableBalance should be >= 0.0
      balanceDetails.totalPendingBalance should be >= 0.0
      balanceDetails.totalBalance should be >= 0.0
      balanceDetails.totalCreditAvailable should be >= 0.0

      if (balanceDetails.totalPayableBalance > 0) {
        balanceDetails.earliestPayableDueDate shouldBe defined
      }
      if (balanceDetails.totalPendingBalance > 0) {
        balanceDetails.earliestPendingDueDate shouldBe defined
      }
    }

    "generate consistent data relationships" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails.get
      val payments = response.paymentHistoryDetails.get
      val refunds = response.refundDetails.get

      val chargeIds = charges.map(_.chargeId)
      payments.foreach { payment =>
        payment.allocationReference.get.foreach { ref =>
          chargeIds should contain(ref)
        }
      }

      val totalPayments = payments.map(_.paymentAmount).sum
      val totalCharges = charges.map(_.chargeAmount).sum

      if (totalPayments > totalCharges) {
        refunds should not be empty
      }
    }

    "handle multi-year date ranges correctly" in {
      val fromDate = LocalDate.of(2022, 1, 1)
      val toDate = LocalDate.of(2024, 12, 31)
      val response = ResponseGenerator.generateResponse(fromDate, toDate)

      val charges = response.chargeDetails.get

      val chargeYears = charges.map(_.creationDate.getYear)
      chargeYears should contain allOf (2022, 2023, 2024)

      charges.foreach { charge =>
        val year = charge.creationDate.getYear
        charge.taxYear shouldBe s"$year-${year + 1}"
      }
    }

    "correctly categorize charges by due date relative to today" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val charges = response.chargeDetails.get

      val overdueCharges = charges.filter(_.dueDate.isBefore(today))
      val payableCharges = charges.filter { charge =>
        charge.dueDate.isBefore(today.plusDays(30)) && charge.dueDate.isAfter(today)
      }
      val pendingCharges = charges.filter(_.dueDate.isAfter(today.plusDays(30)))

      val allCategorizedCharges = overdueCharges ++ payableCharges ++ pendingCharges
      allCategorizedCharges.size shouldBe charges.size

      payableCharges.foreach { charge =>
        charge.dueDate should be > today
        charge.dueDate should be < today.plusDays(30)
      }

      overdueCharges.foreach { charge =>
        charge.dueDate should be < today
      }

      pendingCharges.foreach { charge =>
        charge.dueDate should be > today.plusDays(30)
      }
    }

    "calculate balance details correctly based on charge categorization" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val balanceDetails = response.balanceDetails
      val charges = response.chargeDetails.get

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

    "handle edge cases for payable charge date boundaries" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val charges = response.chargeDetails.get

      charges.foreach { charge =>
        val dueDate = charge.dueDate

        if (dueDate.isEqual(today)) {
          val isPayable = dueDate.isBefore(today.plusDays(30)) && dueDate.isAfter(today)
          isPayable shouldBe false
        }

        if (dueDate.isEqual(today.plusDays(30))) {
          val isPayable = dueDate.isBefore(today.plusDays(30)) && dueDate.isAfter(today)
          isPayable shouldBe false
        }

        if (dueDate.isEqual(today.plusDays(29))) {
          val isPayable = dueDate.isBefore(today.plusDays(30)) && dueDate.isAfter(today)
          isPayable shouldBe true
        }

        if (dueDate.isEqual(today.plusDays(1))) {
          val isPayable = dueDate.isBefore(today.plusDays(30)) && dueDate.isAfter(today)
          isPayable shouldBe true
        }
      }
    }

    "ensure payable charges exclude today and charges 30+ days away" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val charges = response.chargeDetails.get

      val payableCharges = charges.filter { charge =>
        charge.dueDate.isBefore(today.plusDays(30)) && charge.dueDate.isAfter(today)
      }

      payableCharges.foreach { charge =>
        charge.dueDate should not be today
      }

      payableCharges.foreach { charge =>
        charge.dueDate should be < today.plusDays(30)
      }

      payableCharges.foreach { charge =>
        charge.dueDate should be > today
      }
    }

    "validate coded out details for overdue charges" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val balanceDetails = response.balanceDetails
      val charges = response.chargeDetails.get

      val overdueCharges = charges.filter(_.dueDate.isBefore(today))
      val overdueChargesWithOutstanding = overdueCharges.filter(_.outstandingAmount > 0)

      if (overdueChargesWithOutstanding.nonEmpty) {
        balanceDetails.codedOutDetail shouldBe defined
        val codedOutDetail = balanceDetails.codedOutDetail.get.head

        codedOutDetail.totalAmount should be > 0.0
        codedOutDetail.effectiveStartDate should not be null
        codedOutDetail.effectiveEndDate should not be null
        codedOutDetail.effectiveEndDate should be > codedOutDetail.effectiveStartDate

        val expectedOverdueBalance =
          overdueChargesWithOutstanding.map(_.outstandingAmount).sum - codedOutDetail.totalAmount
        balanceDetails.totalOverdueBalance shouldBe expectedOverdueBalance
      }
    }

    "verify total balance calculation includes all charge categories" in {
      val response = ResponseGenerator.generateResponse(fromDate, toDate)
      val balanceDetails = response.balanceDetails

      val calculatedTotal = balanceDetails.totalOverdueBalance +
        balanceDetails.totalPayableBalance +
        balanceDetails.totalPendingBalance

      balanceDetails.totalBalance should equal(calculatedTotal)
    }
  }
}
