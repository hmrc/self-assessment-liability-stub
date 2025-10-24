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

import models.{Amendment, ChargeDetails, PaymentHistoryDetails}
import utils.ResponseGenerator.{random, roundValue, today}

import java.time.LocalDate

object chargesUtils {
  private val randomChargeType = {
    random.shuffle(List("ITSA", "Penalty", "PAYE", "POA")).head
  }

  def generateCharge(
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
      generateChargeDetailsModel(
        paymentItem,
        creationDate,
        chargeAmount,
        isNotRecentStatement,
        processDate,
        amendmentAmount,
        outstandingAmount
      )
    }
  }

  private def generateChargeDetailsModel(
      paymentItem: PaymentHistoryDetails,
      creationDate: LocalDate,
      chargeAmount: BigDecimal,
      isNotRecentStatement: Boolean,
      processDate: LocalDate,
      amendmentAmount: BigDecimal,
      outstandingAmount: BigDecimal
  ): ChargeDetails = {
    ChargeDetails(
      chargeId =
        paymentItem.allocationReference.headOption.getOrElse(ResponseGenerator.generateId()),
      creationDate = creationDate,
      chargeType = randomChargeType,
      chargeAmount = chargeAmount,
      taxYear = paymentItem.paymentDate.getYear.toString,
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
        isPaymentRelated = true,
        paymentMethod = payment.paymentMethod,
        paymentDate = Some(payment.paymentDate)
      )
    )
  }

  private def biasedRandomMultiplication(value: BigDecimal): BigDecimal = {
    val biasedList: List[BigDecimal] =
      (BigDecimal(90) to BigDecimal(105) by 1).map(i => i / 100).toList ++
        List(1, 1, 1, 1, 1)
    roundValue(value * random.shuffle(biasedList).head)
  }
}
