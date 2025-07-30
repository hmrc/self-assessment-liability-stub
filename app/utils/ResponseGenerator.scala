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
  AccruingInterestDateRange,
  Amendments,
  BalanceDetails,
  ChargeDetails,
  CodedOutDetail,
  HipResponse,
  PaymentHistoryDetails,
  RefundDetails
}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, MonthDay}
import scala.util.Random

object ResponseGenerator {
  private val random = new Random()
  private val chargeTypes = List("ITSA", "Penalty", "PAYE")
  private val amendmentTypes = List("payment", "credit", "adjustment")
  private val paymentMethods = List("bank transfer", "card", "direct debit", "cheque")
  private val refundStatuses = List("processed", "pending", "rejected")

  def generateResponse(fromYear: Int, toYear: Int): HipResponse = {
    var allCharges: Set[ChargeDetails] = Set[ChargeDetails]()
    var allRefunds: Set[RefundDetails] = Set[RefundDetails]()
    var allHistory: Set[PaymentHistoryDetails] = Set[PaymentHistoryDetails]()

    (fromYear to toYear).foreach(year => {
      val numCharges = random.nextInt(2) + 1

      val charges = (1 to numCharges).map(_ => generateCharge(year)).toSet
      val refunds = generateRefunds(year)
      val paymentHistory = generatePaymentHistory(year, charges)

      allCharges ++= charges
      allRefunds ++= refunds
      allHistory ++= paymentHistory
    })

    val balanceDetails = generateBalanceDetails(fromYear, allCharges)

    HipResponse(
      balanceDetails,
      if (allCharges.isEmpty) None else Some(allCharges),
      if (allRefunds.isEmpty) None else Some(allRefunds),
      if (allHistory.isEmpty) None else Some(allHistory)
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
    val chargeAmount = random.nextInt(5000) + 500
    val outstandingAmount = chargeAmount * random.nextDouble()
    val chargeType = chargeTypes(random.nextInt(chargeTypes.length))

    val amendments = if (random.nextBoolean()) {
      Some((1 to random.nextInt(3) + 1).map(_ => generateAmendment(year, chargeAmount)).toSet)
    } else None

    val codedOut = if (random.nextBoolean() && year < getTaxYear(LocalDate.now())) {
      Some(Set(generateCodedOutDetail(year)))
    } else None
    val interestStartDate = generateDateInYear(year + 1)
    val interestEndDate = generateDateInYear(year + 1, isEndOfYear = true)
    val isLate = random.nextBoolean()
    val interestAmount = random.nextInt(200).toDouble
    ChargeDetails(
      chargeId = generateChargeId(),
      creationDate = generateDateInYear(year),
      chargeType = chargeType,
      chargeAmount = setCurrencyPrecision(chargeAmount),
      outstandingAmount = setCurrencyPrecision(outstandingAmount),
      taxYear = s"$year-${year + 1}",
      dueDate = generateDateInYear(year + 1),
      interestAmountDue = if isLate then Some(setCurrencyPrecision(interestAmount)) else None,
      accruingInterest = if isLate then Some(setCurrencyPrecision(interestAmount)) else None,
      accruingInterestDateRange =
        if isLate then Some(AccruingInterestDateRange(interestStartDate, interestEndDate))
        else None,
      accruingInterestRate = if isLate then Some(0.05) else None,
      amendments = amendments,
      codedOutDetail = codedOut
    )
  }

  def generateAmendment(year: Int, maxAmount: Double): Amendments = {
    val amendmentReason = amendmentTypes(random.nextInt(amendmentTypes.length))
    val amendmentAmount = maxAmount * random.nextDouble()

    Amendments(
      amendmentDate = generateDateInYear(year),
      amendmentAmount = setCurrencyPrecision(amendmentAmount),
      amendmentReason = amendmentReason,
      newChargeBalance = Some(setCurrencyPrecision(maxAmount - amendmentAmount)),
      paymentMethod =
        if (amendmentReason == "payment")
          Some(paymentMethods(random.nextInt(paymentMethods.length)))
        else None,
      paymentDate = if (amendmentReason == "payment") Some(generateDateInYear(year)) else None
    )
  }

  def generateCodedOutDetail(year: Int): CodedOutDetail = {
    CodedOutDetail(
      amount = Some(setCurrencyPrecision(random.nextInt(500) + 100)),
      effectiveDate = Some(generateDateInYear(year)),
      taxYear = Some(s"$year-${year + 1}"),
      effectiveTaxYear = Some(s"${year + 1}-${year + 2}")
    )
  }

  def generateRefunds(year: Int): Set[RefundDetails] = {
    if (random.nextBoolean() && year < getTaxYear(LocalDate.now())) {
      val numRefunds = random.nextInt(2) + 1
      (1 to numRefunds)
        .map(_ => {
          val requestAmount = setCurrencyPrecision(random.nextInt(1000) + 100)
          val interest = setCurrencyPrecision(requestAmount * 0.015)

          RefundDetails(
            issueDate = generateDateInYear(year),
            refundMethod = Some(paymentMethods(random.nextInt(paymentMethods.length))),
            refundRequestDate = Some(generateDateInYear(year - 1, isEndOfYear = true)),
            refundRequestAmount = requestAmount,
            refundReference = Some(generatePaymentReference()),
            interestAddedToRefund = Some(interest),
            refundActualAmount = setCurrencyPrecision(requestAmount + interest),
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
            paymentAmount = setCurrencyPrecision(amendment.amendmentAmount),
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
    val totalCodedOut = charges.flatMap(_.codedOutDetail.getOrElse(Set.empty)).flatMap(_.amount).sum

    BalanceDetails(
      totalOverdueBalance =
        if (year < getTaxYear(LocalDate.now())) setCurrencyPrecision(totalOutstanding) else 0.00,
      totalPayableBalance = setCurrencyPrecision(totalOutstanding * random.nextDouble()),
      payableDueDate = generateDateInYear(year, isEndOfYear = true),
      totalPendingBalance = setCurrencyPrecision(totalOutstanding + random.nextInt(2000)),
      pendingDueDate = generateFutureDate(year),
      totalBalance = setCurrencyPrecision(totalChargeAmount),
      totalCodedOut = totalCodedOut,
      totalCreditAvailable = setCurrencyPrecision(random.nextInt(1000))
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

  def setCurrencyPrecision(d: Double): Double = {
    (math rint d * 100) / 100
  }
}
