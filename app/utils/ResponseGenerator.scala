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

import models.{AccruingInterestDateRange, Amendments, BalanceDetails, ChargeDetails, CodedOutDetail, HipResponse, PaymentHistoryDetails, RefundDetails}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, MonthDay}
import scala.collection.mutable
import scala.util.Random

object ResponseGenerator {
  private val random = new Random()
  private val chargeTypes = List("ITSA", "Penalty", "PAYE")
  private val amendmentTypes = List("payment", "credit")
  private val paymentMethods = List("bank transfer", "card", "direct debit", "cheque")
  private val refundStatuses = List("processed", "pending", "rejected")

  def generateResponse(fromDate: LocalDate, toDate: LocalDate): HipResponse = {
    val records = (fromYear to toYear).map { year =>
      val numChargesPerYear = random.nextInt(2) + 1
      val charges = (1 to numChargesPerYear).map(_ => generateCharge(year)).toSet
      val refunds = generateRefunds(year)
      val paymentHistory = generatePaymentHistory(year, charges)

      (charges, refunds, paymentHistory)
    }

    val allCharges = records.flatMap(_._1).toSet
    val allRefunds = records.flatMap(_._2).toSet
    val allPaymentHistory = records.flatMap(_._3).toSet

    val balanceDetails = generateBalanceDetails(fromYear, allCharges)

    val codedOut = if (random.nextBoolean()) Some(Set(generateCodedOutDetail(year) else None

    HipResponse(
      balanceDetails,
      Some(allCharges),
      Some(allRefunds),
      Some(allPaymentHistory)
    )
  }

  def getTaxYear(fromDate: LocalDate): Int = {
    val taxYearStart: MonthDay = MonthDay.parse("--04-06")
    val fromDateMonthDay: MonthDay = MonthDay.from(fromDate)

    if (fromDateMonthDay.isBefore(taxYearStart)) {
      fromDate.getYear - 1
    } else {
      fromDate.getYear
    }
  }

  def generateCharge(year: Int): ChargeDetails = {
    val totalChargeAmount = random.between(500, 50000)
    val chargeType = random.shuffle(chargeTypes).head

    val amendments = if (year < LocalDate.now().getYear) {
      Some(generateAmendments(year, totalChargeAmount))
    } else None

    val outstandingAmount = totalChargeAmount - amendments.getOrElse(Set.empty).map(_.amendmentAmount).sum
    val isLate = random.nextBoolean()
    val interestStartDate = generateDateInYear(year + 1)
    val interestEndDate = generateDateInYear(year + 1, isEndOfYear = true)

    val interestAmount = random.nextInt(200).toDouble
    ChargeDetails(
      chargeId = generateChargeId(),
      creationDate = LocalDate().withYear(year),
      chargeType = chargeType,
      chargeAmount = totalChargeAmount,
      outstandingAmount = outstandingAmount,
      taxYear = s"$year-${year + 1}",
      dueDate = LocalDate().withYear(year).plusMonths(3),
      interestAmountDue = if isLate then Some(interestAmount) else None,
      accruingInterest = if isLate then Some(interestAmount) else None,
      accruingInterestDateRange =
        if isLate then Some(AccruingInterestDateRange(interestStartDate, interestEndDate))
        else None,
      accruingInterestRate = if isLate then Some(0.05) else None,
      amendments = amendments
    )
  }

  def generateAmendments(year: Int, totalChargeAmount: Double): Set[Amendments] = {
    val numberOfAmendments = random.nextInt(3) + 1
    var remainingAmount = totalChargeAmount
    val amendments = mutable.Set.empty[Amendments]

    for (i <- 1 to numberOfAmendments) {
      if (remainingAmount > 0) {
        val maxAmendmentAmount = if (i == numberOfAmendments) {
          remainingAmount
        } else {
          math.min(remainingAmount * 0.8, remainingAmount - (numberOfAmendments - i) * (totalChargeAmount * 0.1))
        }

        val amendment = generateAmendment(year, maxAmendmentAmount, remainingAmount)
        amendments += amendment
        remainingAmount -= amendment.amendmentAmount
      }
    }

    amendments.toSet
  }

  def generateAmendment(year: Int, maxAmount: Double, currentBalance: Double): Amendments = {
    val amendmentReason = random.shuffle(amendmentTypes).head

    // Ensure amendment amount doesn't exceed the available balance and is reasonable
    val minAmount = math.max(1.0, maxAmount * 0.1)
    val amendmentAmount = if (maxAmount <= minAmount) {
      maxAmount
    } else {
      random.between(minAmount, maxAmount)
    }

    Amendments(
      amendmentDate = generateDateInYear(year),
      amendmentAmount = amendmentAmount,
      amendmentReason = amendmentReason,
      newChargeBalance = Some(currentBalance - amendmentAmount),
      paymentMethod =
        if (amendmentReason == "payment")
          Some(paymentMethods(random.nextInt(paymentMethods.length)))
        else None,
      paymentDate = if (amendmentReason == "payment") Some(generateDateInYear(year)) else None
    )
  }

  def generateCodedOutDetail(year: Int, maxAmount: Double): CodedOutDetail = {
    CodedOutDetail(
      totalAmount = random.between(500, maxAmount),
      effectiveStartDate = ???,
      effectiveEndDate = ???
    )
  }

  def generateRefunds(year: Int): Set[RefundDetails] = {
    if (random.nextBoolean() && year < getTaxYear(LocalDate.now())) {
      val numRefunds = random.nextInt(2) + 1
      (1 to numRefunds)
        .map(_ => {
          val requestAmount = random.nextInt(1000) + 100
          val interest = requestAmount * 0.015

          RefundDetails(
            issueDate = generateDateInYear(year),
            refundMethod = Some(paymentMethods(random.nextInt(paymentMethods.length))),
            refundRequestDate = Some(generateDateInYear(year - 1, isEndOfYear = true)),
            refundRequestAmount = requestAmount,
            refundReference = Some(generatePaymentReference()),
            interestAddedToRefund = Some(interest),
            refundActualAmount = requestAmount + interest,
            refundStatus = Some(refundStatuses(random.nextInt(refundStatuses.length)))
          )
        })
        .toSet
    } else Set.empty
  }

  def generatePaymentHistory(
      year: Int,
      charges: Set[ChargeDetails]
  ): Set[PaymentHistoryDetails] = {
    charges.flatMap { charge =>
      charge.amendments
        .getOrElse(Set.empty)
        .filter(_.amendmentReason == "payment")
        .map { amendment =>
          PaymentHistoryDetails(
            paymentAmount = amendment.amendmentAmount,
            paymentId = generatePaymentReference(),
            paymentMethod = amendment.paymentMethod.getOrElse("bank_transfer"),
            paymentDate = amendment.paymentDate.getOrElse(generateDateInYear(year)),
            dateProcessed = amendment.amendmentDate,
            allocationReference = Some(charge.chargeId)
          )
        }
    }
  }

  def generateBalanceDetails(year: Int, charges: Set[ChargeDetails]): BalanceDetails = {
    val totalOutstanding = charges.map(_.outstandingAmount).sum
    val totalChargeAmount = charges.map(_.chargeAmount).sum
    val codedOutDetail = ???  //implement logic
    BalanceDetails(
      totalOverdueBalance =
        if (year < getTaxYear(LocalDate.now())) totalOutstanding else 0.00,
      totalPayableBalance = totalOutstanding * random.nextDouble(),
      payableDueDate = generateDateInYear(year, isEndOfYear = true),
      totalPendingBalance = totalOutstanding + random.nextInt(2000),
      pendingDueDate = generateFutureDate(year),
      totalBalance = totalChargeAmount,
      totalCreditAvailable = random.nextInt(1000)
    )
  }

  private def generatePaymentReference(): String = random.nextInt(1231232131).toString

  private def generateChargeId(): String = {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val prefix = (1 to 2).map(_ => letters(random.nextInt(letters.length))).mkString
    val numbers = (1 to 7).map(_ => random.nextInt(10)).mkString
    s"$prefix$numbers"
  }

  private def generateDateInYear(year: Int, isEndOfYear: Boolean = false): String = {
    val month = if (isEndOfYear) random.nextInt(6) + 7 else random.nextInt(12) + 1
    val maxDay = month match {
      case 2              => if (year % 4 == 0) 29 else 28
      case 4 | 6 | 9 | 11 => 30
      case _              => 31
    }
    val day = random.nextInt(maxDay) + 1
    LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE)
  }

  private def generateFutureDate(baseYear: Int): String = {
    val futureYear = baseYear + random.nextInt(2) + 1
    generateDateInYear(futureYear)
  }
  
}
