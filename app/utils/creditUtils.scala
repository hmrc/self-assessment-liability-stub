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

import models.{Amendment, ChargeDetails, PaymentHistoryDetails, RefundDetails}
import utils.ResponseGenerator.{random, roundValue, today}

import scala.annotation.tailrec

object creditUtils {

  @tailrec
  def allocateCredit(
      creditAvailable: BigDecimal,
      charges: List[ChargeDetails]
  ): (List[ChargeDetails], BigDecimal) = {
    if (creditAvailable <= BigDecimal(0)) {
      (charges, creditAvailable)
    } else {
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
  }

  def allocateCreditToOutstandingCharges(
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

  private def creditAmendment(charge: ChargeDetails, amount: BigDecimal): ChargeDetails = {
    val remainingBalance = charge.outstandingAmount - amount
    val amendment =
      Amendment(
        amendmentDate = today.minusDays(random.nextInt(20)),
        amendmentAmount = amount,
        amendmentReason = "Credit applied from overpayment",
        isPaymentRelated = true,
        paymentMethod = None,
        paymentDate = None
      )

    charge.copy(amendments = charge.amendments :+ amendment, outstandingAmount = remainingBalance)
  }

  private def getOverdueOrFutureCharges(charges: List[ChargeDetails]): Option[ChargeDetails] = {
    val overdueChargesWithOutstanding = charges.filter { charge =>
      charge.dueDate.isBefore(today) && charge.outstandingAmount > BigDecimal(0)
    }

    if (overdueChargesWithOutstanding.nonEmpty) {
      overdueChargesWithOutstanding.toSet.minByOption(_.dueDate)
    } else {
      charges
        .filter(charge =>
          charge.amendments.isEmpty &&
            charge.outstandingAmount > BigDecimal(0)
        )
        .minByOption(_.dueDate)
    }
  }
}
