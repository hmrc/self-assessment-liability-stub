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

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.util.Random

object ResponseGenerator {
  private lazy val random = new Random()
  private val randomChargeType = random.shuffle(List("ITSA", "Penalty", "PAYE", "POA")).head
  private val randomStatementMonth = random.shuffle(List(4, 10)).head
  private val randomPaymentMethod =
    random.shuffle(List("bank transfer", "card", "direct debit", "cheque")).head

  def generateResponse(fromDate: LocalDate, toDate: LocalDate): HipResponse = {
    val records = (fromDate.getYear to toDate.getYear).map { year =>
      val chooseRandomStatementDate: LocalDate = LocalDate.of(year, randomStatementMonth, 1)
      val numberOfStatementsPerYear = random.nextInt(2) + 1
      val payments = (1 to numberOfStatementsPerYear)
        .map(_ => generatePaymentHistory(chooseRandomStatementDate))
        .toSet
      val charges = generateCharge(chooseRandomStatementDate, payments)
      val refunds = calculateRefunds(payments, charges)
      (charges, payments, refunds)
    }

    val allCharges = records.flatMap(_._1).toSet
    val allPaymentHistory = records.flatMap(_._2).toSet
    val allRefunds = records.flatMap(_._3).toSet

    val balanceDetails = generateBalanceDetails(allCharges, allPaymentHistory, allRefunds)

    HipResponse(
      balanceDetails,
      Some(allCharges),
      Some(allRefunds),
      Some(allPaymentHistory)
    )
  }

  private def generatePaymentHistory(statementDate: LocalDate): PaymentHistoryDetails = {
    val paymentDate = statementDate.plusDays(random.nextInt(59))
    val paymentAmount = random.between(500, 50000)
    PaymentHistoryDetails(
      paymentAmount = paymentAmount,
      paymentReference = generatePaymentReference(),
      paymentMethod = Some(randomPaymentMethod),
      paymentDate = paymentDate,
      processedDate = Some(paymentDate.plusDays(random.nextInt(6))),
      allocationReference = Some(List(generateChargeId()))
    )
  }

  private def biasedRandomMultiplication(value: Double): Double = {
    val biasedList: List[Double] = (90 to 105 by  1).map(_ / 100.toDouble).toList ++ List(1,1,1,1,1)
    roundValue(value * random.shuffle(biasedList).head)
  }

  def generateCharge(
      statementDate: LocalDate,
      payments: Set[PaymentHistoryDetails]
  ): Set[ChargeDetails] = {
    val isNotRecentStatement: Boolean = statementDate.isBefore(LocalDate.now().minusDays(45))
    val dueDate: LocalDate = statementDate.plusMonths(2)
    val taxYear = s"${statementDate.getYear}-${statementDate.getYear + 1}"

    payments.map { paymentItem =>
      val processDate = paymentItem.processedDate.getOrElse(dueDate.minusDays(random.nextInt(10)))
      val chargeAmount: Double = biasedRandomMultiplication(paymentItem.paymentAmount)
      val amendmentAmount =
        if chargeAmount < paymentItem.paymentAmount then chargeAmount else paymentItem.paymentAmount
      val outstandingAmount = roundValue(if isNotRecentStatement then chargeAmount - amendmentAmount else chargeAmount)
      val interest = calculateInterestDue(dueDate, outstandingAmount)
      val isInterestAccrued = if chargeAmount > amendmentAmount & dueDate.isBefore(LocalDate.now()) then true else false
      ChargeDetails(
        chargeId = paymentItem.allocationReference.map(_.head).getOrElse(generateChargeId()),
        creationDate = statementDate,
        chargeType = randomChargeType,
        chargeAmount = chargeAmount,
        taxYear = taxYear,
        dueDate = dueDate,
        amendments =
          if isNotRecentStatement then
            Some(generateAmendment(processDate, amendmentAmount, paymentItem))
          else None,
        outstandingAmount =
          outstandingAmount + interest.getOrElse(0.0),
        outstandingInterestDue =
          if isInterestAccrued then interest else None,
        accruingInterest =
          if isInterestAccrued then interest else None,
        accruingInterestPeriod = if isInterestAccrued then interest.map(_=>AccruingInterestPeriod(dueDate.plusMonths(1), LocalDate.now())) else None,
        accruingInterestRate = if isInterestAccrued then Some(0.05) else None
      )
    }
  }

  private def calculateInterestDue(
      dueDate: LocalDate,
      outstandingAmount: Double
  ): Option[Double] = {
    Some(roundValue(outstandingAmount * (ChronoUnit.MONTHS.between(dueDate.plusMonths(1), LocalDate.now()) / 12) * 0.05))
  }

  def generateAmendment(
      amendmentDate: LocalDate,
      amount: Double,
      payment: PaymentHistoryDetails
  ): Set[Amendments] = {
    Set(
      Amendments(
        amendmentDate = amendmentDate,
        amendmentAmount = amount,
        amendmentReason = "payment",
        paymentMethod = payment.paymentMethod,
        paymentDate = Some(payment.paymentDate)
      )
    )
  }

  private def calculateRefunds(
      payments: Set[PaymentHistoryDetails],
      charges: Set[ChargeDetails]
  ): Set[RefundDetails] = {
    val randomDayOfRefund = random.nextInt(45)
    val remainingBalance = payments.map(_.paymentAmount).sum - charges.map(_.chargeAmount).sum
    val requestDate = payments
      .map(
        _.paymentDate
      ).max.plusDays(randomDayOfRefund)

    val processedDate = requestDate.plusDays(randomDayOfRefund)
    if (remainingBalance > 0) {
      val biasedList = (85 to 99 by 1).map(_ / 100.toDouble).toList ++ List(1.0,1.0,1.0,1.0,1.0)
      val randomRefundAmount: Double = roundValue(remainingBalance * random.shuffle(biasedList).head)
      val interest = (ChronoUnit.DAYS.between(requestDate, processedDate) / 28).toDouble * 0.001 * randomRefundAmount
      val getOverpaymentDates = findOverpaymentDates(payments,charges)
      Set(
        RefundDetails(
          refundDate = processedDate,
          refundMethod = Some(randomPaymentMethod),
          refundRequestDate = Some(requestDate),
          refundRequestAmount = randomRefundAmount,
          refundDescription = Some(s"Refund for overpayment(s) made on ${getOverpaymentDates.mkString(" and ")}"),
          interestAddedToRefund = Some(interest),
          totalRefundAmount = randomRefundAmount + interest,
          refundStatus =
            if processedDate.isAfter(LocalDate.now()) then Some("pending") else Some("accepted")
        )
      )
    } else Set.empty

  }


  private def findOverpaymentDates(
                                    payments: Set[PaymentHistoryDetails],
                                    charges: Set[ChargeDetails]
                                  ): Set[LocalDate] = {
    val chargeMap = charges.map(charge => charge.chargeId -> charge.chargeAmount).toMap

    payments.flatMap { payment =>
      payment.allocationReference.flatMap { refs =>
        refs.headOption.flatMap { chargeRef =>
          chargeMap.get(chargeRef).collect {
            case chargeAmount if payment.paymentAmount > chargeAmount => payment.paymentDate
          }
        }
      }
    }
  }
  def generateBalanceDetails(
      charges: Set[ChargeDetails],
      payments: Set[PaymentHistoryDetails],
      refunds: Set[RefundDetails]
  ): BalanceDetails = {
    val today = LocalDate.now()
    val allOverDueCharges = charges.filter(_.dueDate.isBefore(today))
    val overDueChargesWithAnOutstandingAmount = allOverDueCharges.filter(_.outstandingAmount > 0)
    val getCodedOut = overDueChargesWithAnOutstandingAmount.headOption.map{overdueCharge=> Set(CodedOutDetail(totalAmount = overdueCharge.outstandingAmount, effectiveStartDate = overdueCharge.dueDate, effectiveEndDate = overdueCharge.dueDate.plusYears(1).withMonth(4).withDayOfMonth(5)))}
    val totalOverDueBalance = overDueChargesWithAnOutstandingAmount.map(_.outstandingAmount).sum - getCodedOut.map(_.map(_.totalAmount).sum).getOrElse(0.00)
    val allPayableCharges = charges.filter{charge=>
      charge.dueDate.isBefore(today.plusDays(30)) & charge.dueDate.isAfter(today)
    }
    val totalPayableBalance = allPayableCharges.map(_.outstandingAmount).sum
    val allPendingCharges = charges.filter(_.dueDate.isAfter(today.plusDays(30)))
    val totalPendingBalance = allPendingCharges.map(_.outstandingAmount).sum
    val totalBalance = roundValue(totalOverDueBalance + totalPayableBalance + totalPendingBalance)
    val totalCredit = roundValue(calculateCredit(totalBalance, payments.map(_.paymentAmount).sum,charges.map(_.chargeAmount).sum,refunds.map(_.refundRequestAmount).sum))
    BalanceDetails(
      totalOverdueBalance = roundValue(totalOverDueBalance),
      totalPayableBalance = roundValue(totalPayableBalance),
      earliestPayableDueDate = getTheEarliestDueDate(allPayableCharges),
      totalPendingBalance = roundValue(totalPendingBalance),
      earliestPendingDueDate = getTheEarliestDueDate(allPendingCharges),
      totalBalance =totalBalance ,
      totalCreditAvailable = totalCredit,
      codedOutDetail = getCodedOut
    )
  }
  private def calculateCredit(totalBalance: Double, totalPaymentAmount: Double, totalChargeAmount: Double, totalRefundMade: Double): Double ={
    if totalBalance > 0 | totalChargeAmount > totalPaymentAmount then 0.00 else totalPaymentAmount - totalChargeAmount - totalRefundMade
  }

  private def getTheEarliestDueDate(charges: Set[ChargeDetails]): Option[LocalDate] = {
    if charges.nonEmpty & charges.map(_.outstandingAmount).sum > 0 then Some(charges.map(_.dueDate).min) else None
  }

  private def roundValue(num: Double): Double = {
    BigDecimal(num).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  private def generatePaymentReference(): String = random.nextInt(1231232131).toString

  private def generateChargeId(): String = {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val prefix = (1 to 2).map(_ => letters(random.nextInt(letters.length))).mkString
    val numbers = (1 to 7).map(_ => random.nextInt(10)).mkString
    s"$prefix$numbers"
  }

}
