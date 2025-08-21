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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.util.Random

object ResponseGenerator extends Logging {
  private lazy val random = new Random()
  private val randomChargeType = random.shuffle(List("ITSA", "Penalty", "PAYE", "POA")).head
  private val randomPaymentMethod =
    random.shuffle(List("bank transfer", "card", "direct debit", "cheque")).head
  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
  private val today: LocalDate = LocalDate.now()
  private val randomStatementMonth = random.shuffle((1 to today.getMonth.getValue + 2).toList).head
  private val randomStatementDay = random.nextInt(30)
  def generateResponse(fromDate: LocalDate, toDate: LocalDate): HipResponse = {
    val records = (fromDate.getYear to toDate.getYear).map { year =>
      val chooseRandomStatementDate: LocalDate =
        LocalDate.of(year, randomStatementMonth, randomStatementDay)
      logger.info(s"$chooseRandomStatementDate")
      val numberOfStatementsPerYear = random.nextInt(2) + 1
      val payments = (1 to numberOfStatementsPerYear)
        .map(_ => generatePaymentHistory(chooseRandomStatementDate))
        .toList
      val charges = generateCharge(chooseRandomStatementDate, payments)
      val refunds = calculateRefunds(payments, charges)
      (charges, payments, refunds)
    }

    val allCharges = records.flatMap(_._1).toList
    val allPaymentHistory = records.flatMap(_._2).toList
    val allRefunds = records.flatMap(_._3).toList
    val allChargesWithRefundAllocated =
      allocateCreditToFutureCharges(allCharges, allPaymentHistory, allRefunds)
    val balanceDetails = generateBalanceDetails(
      allChargesWithRefundAllocated._1,
      allPaymentHistory,
      allChargesWithRefundAllocated._2
    )

    HipResponse(
      balanceDetails,
      allChargesWithRefundAllocated._1,
      allRefunds,
      allPaymentHistory
    )
  }

  private def allocateCreditToFutureCharges(
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
      getOverdueOrFutureCharges(charges)
        .map { eligibleCharge =>
          val creditToApply = totalCreditAvailable.min(eligibleCharge.outstandingAmount)
          val creditLeftAfterAssignment = totalCreditAvailable - creditToApply

          val updatedCharge = creditAmendment(eligibleCharge, creditToApply)
          val updatedCharges = charges diff List(eligibleCharge) ++ List(updatedCharge)

          (updatedCharges, creditLeftAfterAssignment)
        }
        .getOrElse((charges, BigDecimal(0)))
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
    val remainingBalance = charge.chargeAmount - amount
    val amendments = List(
      Amendment(
        amendmentDate = today.minusDays(random.nextInt(20)),
        amendmentAmount = amount,
        amendmentReason = "Credit applied from overpayment",
        updatedChargeAmount = Some(remainingBalance),
        paymentMethod = None,
        paymentDate = None
      )
    )
    charge.copy(amendments = amendments, outstandingAmount = remainingBalance)
  }

  private def generatePaymentHistory(statementDate: LocalDate): PaymentHistoryDetails = {
    val paymentDate = statementDate.plusDays(random.nextInt(59))
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

  def generateCharge(
      statementDate: LocalDate,
      payments: List[PaymentHistoryDetails]
  ): List[ChargeDetails] = {
    val isNotRecentStatement: Boolean = statementDate.isBefore(today.minusDays(45))
    val dueDate: LocalDate = statementDate.plusMonths(2)
    val taxYear = s"${statementDate.getYear}-${statementDate.getYear + 1}"

    payments.map { paymentItem =>
      val processDate = paymentItem.processedDate.getOrElse(dueDate.minusDays(random.nextInt(10)))
      val chargeAmount: BigDecimal = biasedRandomMultiplication(paymentItem.paymentAmount)
      val amendmentAmount =
        if (chargeAmount < paymentItem.paymentAmount) chargeAmount else paymentItem.paymentAmount
      val outstandingAmount =
        roundValue(if (isNotRecentStatement) chargeAmount - amendmentAmount else chargeAmount)
      val interest = calculateInterestDue(dueDate, outstandingAmount)
      val isInterestAccrued =
        if (chargeAmount > amendmentAmount && dueDate.isBefore(today)) true else false
      ChargeDetails(
        chargeId = paymentItem.allocationReference.headOption.getOrElse(generateId()),
        creationDate = statementDate,
        chargeType = randomChargeType,
        chargeAmount = chargeAmount,
        taxYear = taxYear,
        dueDate = dueDate,
        amendments =
          if (isNotRecentStatement)
            generateAmendment(processDate, amendmentAmount, paymentItem)
          else List.empty[Amendment],
        outstandingAmount = outstandingAmount + interest.getOrElse(BigDecimal(0)),
        outstandingInterestDue = if (isInterestAccrued) interest else None,
        accruingInterest = if (isInterestAccrued) interest else None,
        accruingInterestPeriod =
          if (isInterestAccrued)
            interest.map(_ => AccruingInterestPeriod(dueDate.plusMonths(1), today))
          else None,
        accruingInterestRate = if (isInterestAccrued) Some(BigDecimal("0.05")) else None
      )
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

  def generateAmendment(
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

  private def calculateRefunds(
      payments: List[PaymentHistoryDetails],
      charges: List[ChargeDetails]
  ): List[RefundDetails] = {
    val randomDayOfRefund = random.nextInt(45)
    val remainingBalance = payments.map(_.paymentAmount).sum - charges.map(_.chargeAmount).sum
    val requestDate = payments
      .map(_.paymentDate)
      .max
      .plusDays(randomDayOfRefund)

    val processedDate = requestDate.plusDays(randomDayOfRefund)
    if (remainingBalance > BigDecimal(0)) {
      val biasedList = (85 to 99 by 1).map(i => BigDecimal(i) / BigDecimal(100)).toList ++
        List(BigDecimal(1), BigDecimal(1), BigDecimal(1), BigDecimal(1), BigDecimal(1))
      val randomRefundAmount: BigDecimal = roundValue(
        remainingBalance * random.shuffle(biasedList).head
      )
      val interest =
        (BigDecimal(ChronoUnit.DAYS.between(requestDate, processedDate)) / BigDecimal(28)) *
          BigDecimal(0.001) * randomRefundAmount
      val getOverpaymentDates = findOverpaymentDates(payments, charges)
      List(
        RefundDetails(
          refundDate = processedDate,
          refundMethod = Some(randomPaymentMethod),
          refundRequestDate = Some(requestDate),
          refundRequestAmount = randomRefundAmount,
          refundDescription = Some(
            s"Refund for overpayment(s) made on ${getOverpaymentDates.map(_.format(dateFormatter)).mkString(" and ")}"
          ),
          interestAddedToRefund = Some(roundValue(interest)),
          totalRefundAmount = randomRefundAmount + interest,
          refundStatus = if (processedDate.isAfter(today)) Some("pending") else Some("accepted")
        )
      )
    } else List.empty
  }

  private def findOverpaymentDates(
      payments: List[PaymentHistoryDetails],
      charges: List[ChargeDetails]
  ): List[LocalDate] = {
    val chargeMap = charges.map(charge => charge.chargeId -> charge.chargeAmount).toMap

    payments.flatMap { payment =>
      payment.allocationReference.flatMap { refs =>
        chargeMap.get(refs).collect {
          case chargeAmount if payment.paymentAmount > chargeAmount => payment.paymentDate
        }
      }
    }
  }

  def generateBalanceDetails(
      charges: List[ChargeDetails],
      payments: List[PaymentHistoryDetails],
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
      .getOrElse(List.empty[CodedOutDetail])
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
