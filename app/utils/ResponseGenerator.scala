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

import models.*
import play.api.Logging

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import scala.annotation.tailrec
import scala.util.Random

object ResponseGenerator extends Logging {
  private lazy val random = new Random()
  private val randomChargeType = random.shuffle(List("ITSA", "Penalty", "PAYE", "POA")).head
  private val randomPaymentMethod =
    random.shuffle(List("bank transfer", "card", "direct debit", "cheque")).head
  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
  private val today: LocalDate = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate

  private def dateGenerator(year: Int): LocalDate = {
    val randomStatementMonths = random.shuffle(List(4, 10)).head
    val randomStatementDays = random.nextInt(30) + 1
    LocalDate.of(year, randomStatementMonths, randomStatementDays)
  }

  def generateResponse(fromDate: LocalDate, toDate: LocalDate): HipResponse = {
    val records = (fromDate.getYear to toDate.getYear).map { year =>
      val numberOfStatementsPerYear = random.nextInt(2) + 1
      val payments = (1 to numberOfStatementsPerYear)
        .map(_ => generatePaymentHistory(dateGenerator(year)))
        .toList
      val charges = generateCharge(payments)
      val updatedChargesWithRefunds = calculateInterestOrGenerateRefund(charges, payments)
      (updatedChargesWithRefunds._1, payments, updatedChargesWithRefunds._2)
    }

    val allCharges = records.flatMap(_._1).toList
    val allPaymentHistory = records.flatMap(_._2).toList
    val allRefunds = records.flatMap(_._3).toList
    val allChargesWithRefundAllocated =
      allocateCreditToOutstandingCharges(allCharges, allPaymentHistory, allRefunds)
    val balanceDetails = generateBalanceDetails(
      allChargesWithRefundAllocated._1,
      allChargesWithRefundAllocated._2
    )

    HipResponse(
      balanceDetails,
      allChargesWithRefundAllocated._1,
      allRefunds,
      allPaymentHistory
    )
  }

  private def calculateInterestOrGenerateRefund(
      charges: List[ChargeDetails],
      payments: List[PaymentHistoryDetails]
  ): (List[ChargeDetails], List[RefundDetails]) = {
    val groupedCharges = charges.groupBy(_.creationDate.getYear)
    val groupedPayments = payments.groupBy(_.paymentDate.getYear)
    val allYears = groupedCharges.keySet ++ groupedPayments.keySet

    val updatedChargesWithRefunds = allYears.map { year =>
      val yearCharges = groupedCharges.getOrElse(year, List.empty)
      val yearPayments = groupedPayments.getOrElse(year, List.empty)
      val outstandingAmount =
        yearPayments.map(_.paymentAmount).sum - yearCharges.map(_.chargeAmount).sum
      if (outstandingAmount < 0) {
        (yearCharges.map(calculateInterest), List.empty)
      } else if (outstandingAmount == 0) {
        (yearCharges, List.empty)
      } else {
        val mostRecentPaymentDate: LocalDate =
          if (yearPayments.nonEmpty) yearPayments.map(_.paymentDate).max
          else LocalDate.ofYearDay(year, today.getDayOfYear)
        (yearCharges, List(generateRefund(outstandingAmount, mostRecentPaymentDate)))
      }
    }.toList

    (updatedChargesWithRefunds.flatMap(_._1), updatedChargesWithRefunds.flatMap(_._2))
  }

  private def isFutureDate(date: LocalDate): Boolean = {
    if date.isBefore(today) then false else true
  }

  private def generateRefund(
      surplus: BigDecimal,
      mostRecentPaymentDate: LocalDate
  ): RefundDetails = {
    val requestDate = mostRecentPaymentDate.plusDays(7 + random.nextInt(23))
    val refundDate = requestDate.plusDays(random.nextInt(60))
    val interest =
      (BigDecimal(ChronoUnit.DAYS.between(requestDate, requestDate)) / BigDecimal(28)) *
        BigDecimal(0.001) * surplus
    RefundDetails(
      refundDate = if isFutureDate(refundDate) then None else Some(refundDate),
      refundMethod = Some(randomPaymentMethod),
      refundRequestDate = if isFutureDate(requestDate) then None else Some(requestDate),
      refundRequestAmount = surplus,
      refundDescription = Some(
        s"Surplus calculated for overpayment(s) made up to ${mostRecentPaymentDate.format(dateFormatter)}"
      ),
      interestAddedToRefund = Some(roundValue(interest)),
      totalRefundAmount = surplus + interest,
      refundStatus = Some("accepted")
    )
  }
  private def calculateInterest(charge: ChargeDetails): ChargeDetails = {
    if (charge.dueDate.plusMonths(1).isAfter(today) || charge.outstandingAmount == 0) {
      charge
    } else {
      val interest = calculateInterestDue(charge.dueDate, charge.outstandingAmount)

      charge.copy(
        outstandingAmount = charge.outstandingAmount + interest.getOrElse(0.0),
        accruingInterest = interest,
        accruingInterestRate = Some(BigDecimal(0.05)),
        accruingInterestPeriod =
          interest.map(_ => AccruingInterestPeriod(charge.dueDate.plusMonths(1), today)),
        outstandingInterestDue = interest
      )
    }
  }

  private def allocateCreditToOutstandingCharges(
      charges: List[ChargeDetails],
      payments: List[PaymentHistoryDetails],
      refunds: List[RefundDetails]
  ): (List[ChargeDetails], BigDecimal) = {

    val totalCreditAvailable = roundValue(
      payments.map(_.paymentAmount).sum -
        charges.map(_.chargeAmount).sum -
        refunds.map(_.refundRequestAmount).sum
    )
    if (totalCreditAvailable <= BigDecimal(0)) {
      (charges, BigDecimal(0))
    } else {
      allocateCredit(totalCreditAvailable, charges)
    }
  }

  @tailrec
  def allocateCredit(
      creditAvailable: BigDecimal,
      charges: List[ChargeDetails]
  ): (List[ChargeDetails], BigDecimal) = {

    getOverdueOrFutureCharges(charges) match {
      case None =>
        (charges, creditAvailable)
      case Some(eligibleCharge) =>
        val creditToApply = creditAvailable.min(eligibleCharge.outstandingAmount)
        val remainingAmount = creditAvailable - creditToApply
        val updatedCharge = creditAmendment(eligibleCharge, creditToApply)
        val newProcessedCharges =
          charges.filterNot(_.chargeId == eligibleCharge.chargeId) :+ updatedCharge

        allocateCredit(remainingAmount, newProcessedCharges)
    }
  }

  private def getOverdueOrFutureCharges(charges: List[ChargeDetails]): Option[ChargeDetails] = {
    val overdueChargesWithOutstanding = charges.filter { charge =>
      charge.dueDate.isBefore(today) && charge.outstandingAmount > BigDecimal(0)
    }

    if (overdueChargesWithOutstanding.nonEmpty) {
      overdueChargesWithOutstanding.toSet.minByOption(_.dueDate)
    } else {
      charges
        .filter(charge => charge.amendments.isEmpty)
        .minByOption(_.dueDate)
    }
  }

  private def creditAmendment(charge: ChargeDetails, amount: BigDecimal): ChargeDetails = {
    val remainingBalance = charge.outstandingAmount - amount
    val amendment =
      Amendment(
        amendmentDate = today.minusDays(random.nextInt(20)),
        amendmentAmount = amount,
        amendmentReason = "Credit applied from overpayment",
        updatedChargeAmount = Some(remainingBalance),
        paymentMethod = None,
        paymentDate = None
      )
    charge.copy(amendments = charge.amendments :+ amendment, outstandingAmount = remainingBalance)
  }

  private def generatePaymentHistory(paymentDate: LocalDate): PaymentHistoryDetails = {
    val paymentAmount = BigDecimal(random.between(500, 50000))
    PaymentHistoryDetails(
      paymentAmount = paymentAmount,
      paymentReference = generateId(),
      paymentMethod = Some(randomPaymentMethod),
      paymentDate = paymentDate,
      processedDate = Some(paymentDate.plusDays(random.nextInt(6))),
      allocationReference = List(generateId())
    )
  }

  private def biasedRandomMultiplication(value: BigDecimal): BigDecimal = {
    val biasedList: List[BigDecimal] =
      (90 to 105 by 1).map(i => BigDecimal(i) / BigDecimal(100)).toList ++
        List(BigDecimal(1), BigDecimal(1), BigDecimal(1), BigDecimal(1), BigDecimal(1))
    roundValue(value * random.shuffle(biasedList).head)
  }

  private def generateCharge(
      payments: List[PaymentHistoryDetails]
  ): List[ChargeDetails] = {
    payments.map { paymentItem =>
      val isNotRecentStatement: Boolean = paymentItem.paymentDate.isBefore(today.minusDays(45))
      val creationDate = paymentItem.paymentDate.minusDays(random.between(10, 15))
      val processDate = paymentItem.processedDate.getOrElse(paymentItem.paymentDate.plusDays(6))
      val chargeAmount: BigDecimal = biasedRandomMultiplication(paymentItem.paymentAmount)
      val amendmentAmount =
        if (chargeAmount < paymentItem.paymentAmount) chargeAmount else paymentItem.paymentAmount
      val outstandingAmount = {
        roundValue(if (isNotRecentStatement) chargeAmount - amendmentAmount else chargeAmount)
      }
      ChargeDetails(
        chargeId = paymentItem.allocationReference.headOption.getOrElse(generateId()),
        creationDate = creationDate,
        chargeType = randomChargeType,
        chargeAmount = chargeAmount,
        taxYear = getTaxYear(paymentItem.paymentDate),
        dueDate = creationDate.plusMonths(1),
        amendments =
          if (isNotRecentStatement)
            generateAmendment(processDate, amendmentAmount, paymentItem)
          else List.empty,
        outstandingAmount = outstandingAmount,
        outstandingInterestDue = None,
        accruingInterest = None,
        accruingInterestPeriod = None,
        accruingInterestRate = None
      )
    }
  }

  def getTaxYear(date: LocalDate): String = {
    if (date.isBefore(LocalDate.of(date.getYear, 4, 6))) {
      s"${date.getYear - 1}-${date.getYear}"
    } else {
      s"${date.getYear}-${date.getYear + 1}"
    }
  }

  private def calculateInterestDue(
      dueDate: LocalDate,
      outstandingAmount: BigDecimal
  ): Option[BigDecimal] = {
    Some(
      roundValue(
        outstandingAmount * (BigDecimal(
          ChronoUnit.MONTHS.between(dueDate.plusMonths(1), today)
        ) / BigDecimal(12)) * BigDecimal(0.05)
      )
    )
  }

  private def generateAmendment(
      amendmentDate: LocalDate,
      amount: BigDecimal,
      payment: PaymentHistoryDetails
  ): List[Amendment] = {
    List(
      Amendment(
        amendmentDate = amendmentDate,
        amendmentAmount = amount,
        amendmentReason = "payment",
        paymentMethod = payment.paymentMethod,
        paymentDate = Some(payment.paymentDate)
      )
    )
  }

  private def generateBalanceDetails(
      charges: List[ChargeDetails],
      creditLeft: BigDecimal
  ): BalanceDetails = {
    val allOverDueCharges = charges.filter(_.dueDate.isBefore(today))
    val overDueChargesWithAnOutstandingAmount =
      allOverDueCharges.filter(_.outstandingAmount > BigDecimal(0))
    val getCodedOut = overDueChargesWithAnOutstandingAmount.headOption
      .map { overdueCharge =>
        List(
          CodedOutDetail(
            totalAmount = overdueCharge.outstandingAmount,
            effectiveStartDate = overdueCharge.dueDate,
            effectiveEndDate = overdueCharge.dueDate.plusYears(1).withMonth(4).withDayOfMonth(5)
          )
        )
      }
      .getOrElse(List.empty)
    val totalOverDueBalance = overDueChargesWithAnOutstandingAmount
      .map(_.outstandingAmount)
      .sum - getCodedOut.map(_.totalAmount).sum
    val allPayableCharges = charges.filter { charge =>
      charge.dueDate.isAfter(today) && charge.dueDate.isBefore(today.plusDays(29))
    }
    val totalPayableBalance = allPayableCharges.map(_.outstandingAmount).sum
    val allPendingCharges = charges.filter(_.dueDate.isAfter(today.plusDays(30)))
    val totalPendingBalance = allPendingCharges.map(_.outstandingAmount).sum
    val totalBalance = roundValue(totalOverDueBalance + totalPayableBalance + totalPendingBalance)
    BalanceDetails(
      totalOverdueBalance = roundValue(totalOverDueBalance),
      totalPayableBalance = roundValue(totalPayableBalance),
      earliestPayableDueDate = getTheEarliestDueDate(allPayableCharges),
      totalPendingBalance = roundValue(totalPendingBalance),
      earliestPendingDueDate = getTheEarliestDueDate(allPendingCharges),
      totalBalance = totalBalance,
      totalCreditAvailable = creditLeft,
      codedOutDetail = getCodedOut
    )
  }

  private def getTheEarliestDueDate(charges: List[ChargeDetails]): Option[LocalDate] = {
    if (charges.nonEmpty && charges.map(_.outstandingAmount).sum > BigDecimal(0))
      Some(charges.map(_.dueDate).min)
    else None
  }

  private def roundValue(num: BigDecimal): BigDecimal = {
    num.setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }

  private def generateId(): String = {
    f"${random.nextLong(1000000000000L)}%012d"
  }

}
