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
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import scala.util.Random

object ResponseGenerator extends Logging {
  lazy val random = new Random()
  val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
  def today(): LocalDate = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate

  val randomPaymentMethod: String = {
    random.shuffle(List("bank transfer", "card", "direct debit", "cheque")).head
  }

  private def dateGenerator(year: Int): LocalDate = {
    val randomStatementMonths = random.shuffle(List(4, 10)).head
    val randomStatementDays = random.nextInt(29) + 1
    val defaultDate = LocalDate.of(year, randomStatementMonths, randomStatementDays)

    if (year < today().getYear) {
      defaultDate
    } else if (year == today().getYear) {
      if (defaultDate.isAfter(today())) today() else defaultDate
    } else {
      today()
    }
  }

  def generateResponse(fromDate: LocalDate, toDate: LocalDate): HipResponse = {
    val records = (fromDate.getYear to toDate.getYear).map { year =>
      val numberOfStatementsPerYear = random.nextInt(2) + 1
      val payments = (1 to numberOfStatementsPerYear)
        .map(_ => PaymentUtils.generatePaymentHistory(dateGenerator(year)))
        .toList
      val charges = ChargesUtils.generateCharge(payments)
      val updatedChargesWithRefunds =
        RefundUtils.calculateInterestOrGenerateRefund(charges, payments)
      (updatedChargesWithRefunds._1, payments, updatedChargesWithRefunds._2)
    }

    val allCharges = records.flatMap(_._1).toList
    val allPaymentHistory = records.flatMap(_._2).toList
    val allRefunds = records.flatMap(_._3).toList
    val allChargesWithRefundAllocated =
      CreditUtils.allocateCreditToOutstandingCharges(allCharges, allPaymentHistory, allRefunds)
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

  private def generateBalanceDetails(
      charges: List[ChargeDetails],
      creditLeft: BigDecimal
  ): BalanceDetails = {
    val allOverDueCharges = charges.filter(_.dueDate.isBefore(today()))
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
      charge.dueDate.isAfter(today()) && charge.dueDate.isBefore(today().plusDays(29))
    }
    val totalPayableBalance = allPayableCharges.map(_.outstandingAmount).sum
    val allPendingCharges = charges.filter(_.dueDate.isAfter(today().plusDays(30)))
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

  def roundValue(num: BigDecimal): BigDecimal = {
    num.setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }

  def generateId(): String = {
    s"ABC${f"${random.nextLong(1000000000000L)}%012d"}"
  }

}
