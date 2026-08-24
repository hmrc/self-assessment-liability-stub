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

import models.{ChargeDetails, PaymentHistoryDetails, RefundDetails}
import utils.ResponseGenerator.{dateFormatter, random, randomPaymentMethod, roundValue, today}

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RefundUtils {

  def calculateInterestOrGenerateRefund(
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

      outstandingAmount match {
        case amount if amount < 0  => (yearCharges.map(calculateInterest), List.empty)
        case amount if amount == 0 => (yearCharges, List.empty)
        case _ =>
          val mostRecentPaymentDate =
            getMostRecentPaymentDate(yearPayments, year, today())
          (yearCharges, List(generateRefund(outstandingAmount, mostRecentPaymentDate)))
      }
    }.toList

    (updatedChargesWithRefunds.flatMap(_._1), updatedChargesWithRefunds.flatMap(_._2))
  }

  private inline def isFutureDate(date: LocalDate): Boolean = date.isAfter(today())

  private def generateRefundDetailsModel(
      refundDate: LocalDate,
      requestDate: LocalDate,
      surplus: BigDecimal,
      mostRecentPaymentDate: LocalDate,
      interest: BigDecimal
  ): RefundDetails = {
    RefundDetails(
      refundDate = if isFutureDate(refundDate) then None else Some(refundDate),
      refundMethod = Some(randomPaymentMethod),
      refundRequestDate = if isFutureDate(requestDate) then None else Some(requestDate),
      refundRequestAmount = surplus,
      refundDescription = Some(
        s"Surplus calculated for overpayment(s) made up to ${mostRecentPaymentDate.format(dateFormatter)}"
      ),
      interestAddedToRefund = Some(ResponseGenerator.roundValue(interest)),
      totalRefundAmount = surplus + interest,
      refundStatus = Some("accepted")
    )
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
    RefundUtils.generateRefundDetailsModel(
      refundDate,
      requestDate,
      surplus,
      mostRecentPaymentDate,
      interest
    )
  }

  private def calculateInterest(charge: ChargeDetails): ChargeDetails = {
    if (isFutureDate(charge.dueDate.plusMonths(1)) || charge.outstandingAmount == 0) {
      charge
    } else {
      val interest =
        calculateInterestDue(charge.dueDate, charge.outstandingAmount)

      charge.copy(
        outstandingAmount = charge.outstandingAmount + interest.getOrElse(0.0),
        accruingInterest = interest,
        accruingInterestRate = Some(BigDecimal(0.05))
      )
    }
  }

  private def getMostRecentPaymentDate(
      payments: List[PaymentHistoryDetails],
      year: Int,
      today: LocalDate
  ): LocalDate = {
    if (payments.nonEmpty) {
      payments.map(_.paymentDate).max
    } else {
      LocalDate.ofYearDay(year, today.getDayOfYear)
    }
  }

  private def calculateInterestDue(
      dueDate: LocalDate,
      outstandingAmount: BigDecimal
  ): Option[BigDecimal] = {
    Some(
      roundValue(
        outstandingAmount * (BigDecimal(
          ChronoUnit.MONTHS.between(dueDate.plusMonths(1), today())
        ) / BigDecimal(12)) * BigDecimal(0.05)
      )
    )
  }

}
