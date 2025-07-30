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

import models.{
  Amendments,
  BalanceDetails,
  ChargeDetails,
  CodedOutDetail,
  HipResponse,
  PaymentHistoryDetails,
  RefundDetails
}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock

import java.time.LocalDate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

class ResponseGeneratorSpec extends AnyWordSpec with Matchers {
  private val random: Random = mock[Random]

  private val chargeDetailsSet: Set[ChargeDetails] =
    Set(ResponseGenerator.generateCharge(2023), ResponseGenerator.generateCharge(2024))
  private val totalOutstanding: Double = chargeDetailsSet.map(_.outstandingAmount).sum
  private val totalChargeAmount = chargeDetailsSet.map(_.chargeAmount).sum
  private val totalCodedOut =
    chargeDetailsSet.flatMap(_.codedOutDetail.getOrElse(Set.empty)).flatMap(_.amount).sum

  "Generate JSON response" should {
    "Generate a response from a given year with optional sets" in {
      val hipResponse: HipResponse = ResponseGenerator.generateResponse(2023, 2024)

      hipResponse.chargeDetails.get.size should (be >= 2 and be <= 4)
      hipResponse.refundDetails.get.size should (be > 0 and be <= 4)
      hipResponse.paymentHistoryDetails.get.size should (be > 0 and be <= 4)
    }

    "Generate a response from a given year without optional sets" in {
      when(ResponseGenerator.generateRefunds(any())).thenReturn(Set.empty)
      when(ResponseGenerator.generatePaymentHistory(any(), any())).thenReturn(Set.empty)

      val hipResponse: HipResponse = ResponseGenerator.generateResponse(2023, 2024)

      hipResponse.chargeDetails shouldBe None
      hipResponse.refundDetails shouldBe None
      hipResponse.paymentHistoryDetails shouldBe None
    }

    "Get the correct tax year" in {
      val date1: LocalDate = LocalDate.parse("2024-07-01")
      val date2: LocalDate = LocalDate.parse("2025-01-01")
      val date3: LocalDate = LocalDate.parse("2025-04-06")
      val date4: LocalDate = LocalDate.parse("2025-07-01")
      ResponseGenerator.getTaxYear(date1) shouldBe 2024
      ResponseGenerator.getTaxYear(date2) shouldBe 2024
      ResponseGenerator.getTaxYear(date3) shouldBe 2025
      ResponseGenerator.getTaxYear(date4) shouldBe 2025
    }

    "Generate charge details with optional values" in {
      when(random.nextBoolean()).thenReturn(true)

      val year: Int = 2025

      val chargeDetails: ChargeDetails = ResponseGenerator.generateCharge(year)

      chargeDetails.chargeId.length should (be >= 2 and be <= 9)
      LocalDate.parse(chargeDetails.creationDate).getYear shouldBe year
      List("ITSA", "Penalty", "PAYE") should contain(chargeDetails.chargeType)
      chargeDetails.chargeAmount should (be >= 500.00 and be < 5500.00)
      chargeDetails.outstandingAmount should (be >= 0.00 and be < 5500.00)
      chargeDetails.taxYear shouldBe s"$year-${year + 1}"
      LocalDate.parse(chargeDetails.dueDate).getYear shouldBe year + 1
      chargeDetails.interestAmountDue.get should (be >= 0.00 and be < 200.00)
      chargeDetails.accruingInterest.get should (be >= 0.00 and be < 200.00)
      LocalDate
        .parse(chargeDetails.accruingInterestDateRange.get.interestStartDate)
        .getYear shouldBe year + 1
      LocalDate
        .parse(chargeDetails.accruingInterestDateRange.get.interestEndDate)
        .getYear shouldBe year + 1
      chargeDetails.accruingInterestRate.get shouldBe 0.05
      chargeDetails.amendments.get.size should (be >= 1 and be <= 3)
      chargeDetails.codedOutDetail.get.size shouldBe 1
    }

    "Generate charge details without optional values" in {
      when(random.nextBoolean()).thenReturn(false)

      val year: Int = 2025

      val chargeDetails: ChargeDetails = ResponseGenerator.generateCharge(year)

      chargeDetails.interestAmountDue shouldBe None
      chargeDetails.accruingInterest shouldBe None
      chargeDetails.accruingInterestDateRange shouldBe None
      chargeDetails.accruingInterestRate shouldBe None
      chargeDetails.amendments shouldBe None
      chargeDetails.codedOutDetail shouldBe None
    }

    "Generate amendment with optional values" in {
      when(random.nextInt(any())).thenReturn(0) // Set the amendment type to "payment".

      val year: Int = 2024
      val maxAmount: Double = 5000.00

      val amendments: Amendments = ResponseGenerator.generateAmendment(year, maxAmount)

      LocalDate.parse(amendments.amendmentDate).getYear shouldBe year
      amendments.amendmentAmount should (be >= 0.00 and be <= maxAmount)
      amendments.amendmentReason shouldBe "payment"
      amendments.newChargeBalance.get shouldBe maxAmount - amendments.amendmentAmount
      List("bank transfer", "card", "direct debit", "cheque") should contain(
        amendments.paymentMethod.get
      )
      LocalDate.parse(amendments.paymentDate.get).getYear shouldBe year
    }

    "Generate amendment without optional values" in {
      when(random.nextInt(any())).thenReturn(1) // Set the amendment type to "credit".

      val amendments: Amendments = ResponseGenerator.generateAmendment(2025, 1000)

      amendments.paymentMethod shouldBe None
      amendments.paymentDate shouldBe None
    }

    "Generate coded out detail" in {
      val year: Int = 2024

      val codedOutDetail: CodedOutDetail = ResponseGenerator.generateCodedOutDetail(year)

      codedOutDetail.amount.get should (be >= 100.00 and be < 600.00)
      LocalDate.parse(codedOutDetail.effectiveDate.get).getYear shouldBe year
      codedOutDetail.taxYear.get shouldBe s"$year-${year + 1}"
      codedOutDetail.effectiveTaxYear.get shouldBe s"${year + 1}-${year + 2}"
    }

    "Generate refunds" in {
      when(random.nextBoolean()).thenReturn(true)

      val year: Int = 2024

      val refundDetails: Set[RefundDetails] = ResponseGenerator.generateRefunds(year)

      refundDetails.size should (be >= 1 and be <= 2)
      refundDetails.foreach(refund => {
        LocalDate.parse(refund.issueDate).getYear shouldBe year
        List("bank transfer", "card", "direct debit", "cheque") should contain(
          refund.refundMethod.get
        )
        LocalDate.parse(refund.refundRequestDate.get).getYear shouldBe year - 1
        refund.refundRequestAmount should (be >= 100.00 and be < 1100.00)
        refund.refundReference.get.toInt should (be >= 0 and be < 1231232131)
        refund.interestAddedToRefund.get shouldBe ResponseGenerator.setCurrencyPrecision(
          refund.refundRequestAmount * 0.015
        )
        refund.refundActualAmount shouldBe refund.refundRequestAmount + refund.interestAddedToRefund.get
        List("processed", "pending", "rejected") should contain(refund.refundStatus.get)
      })
    }

    "Generate empty set refunds" in {
      when(random.nextBoolean()).thenReturn(true)

      val refundDetails: Set[RefundDetails] = ResponseGenerator.generateRefunds(2024)

      refundDetails.size shouldBe 0
    }

    "Generate payment history" in {
      val year: Int = 2024

      val paymentHistoryDetails: Set[PaymentHistoryDetails] =
        ResponseGenerator.generatePaymentHistory(year, chargeDetailsSet)

      paymentHistoryDetails.size shouldBe chargeDetailsSet.size
      paymentHistoryDetails.foreach(charge => {
        // TODO.
      })
    }

    "Generate empty set payment history" in {
      val year: Int = 2024

      // Generate charge details without amendments
      when(random.nextBoolean()).thenReturn(false)
      val chargeDetailsWithoutAmendments: ChargeDetails = ResponseGenerator.generateCharge(year)

      val paymentHistoryDetails: Set[PaymentHistoryDetails] =
        ResponseGenerator.generatePaymentHistory(year, Set(chargeDetailsWithoutAmendments))

      paymentHistoryDetails.size shouldBe 0
    }

    "Generate balance details" in {
      val year: Int = 2024

      val balanceDetails: BalanceDetails =
        ResponseGenerator.generateBalanceDetails(year, chargeDetailsSet)

      balanceDetails.totalOverdueBalance shouldBe totalOutstanding
      balanceDetails.totalPayableBalance should be <= balanceDetails.totalOverdueBalance
      LocalDate.parse(balanceDetails.payableDueDate).getYear shouldBe year
      balanceDetails.totalPendingBalance should (be >= totalOutstanding and be < totalOutstanding + 2000)
      LocalDate.parse(balanceDetails.pendingDueDate).getYear should (be > year and be <= year + 2)
      balanceDetails.totalBalance shouldBe totalChargeAmount
      balanceDetails.totalCodedOut shouldBe totalCodedOut
      balanceDetails.totalCreditAvailable should (be >= 0.00 and be < 1000.00)
    }

    "Set currency precision" in {
      ResponseGenerator.setCurrencyPrecision(0) shouldBe 0
      ResponseGenerator.setCurrencyPrecision(1.111) shouldBe 1.11
      ResponseGenerator.setCurrencyPrecision(1.115) shouldBe 1.12
    }
  }
}
