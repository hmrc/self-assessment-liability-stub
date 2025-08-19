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
import org.scalacheck.Gen

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.jdk.CollectionConverters.*

object HipResponseGenerator {

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
  private val today: LocalDate = LocalDate.now()

  private val chargeTypeGen: Gen[String] = Gen.oneOf("ITSA", "Penalty", "PAYE", "POA")
  private val paymentMethodGen: Gen[String] =
    Gen.oneOf("bank transfer", "card", "direct debit", "cheque")
  private val amountGen: Gen[BigDecimal] = Gen.choose(500, 50000).map(BigDecimal(_))
  private val chargeRefGen: Gen[String] = Gen.listOfN(12, Gen.choose(0, 9)).map(_.mkString)


  private def dateInRange(start: LocalDate, end: LocalDate): Gen[LocalDate] = {
    val daysBetween = ChronoUnit.DAYS.between(start, end).toInt
    if (daysBetween <= 0) Gen.const(start)
    else Gen.choose(0, daysBetween).map(days => start.plusDays(days))
  }

  private val biasedMultiplierGen: Gen[BigDecimal] = {
    val biasedValues = (90 to 105).map(i => BigDecimal(i) / BigDecimal(100)) ++
      List.fill(5)(BigDecimal(1))
    Gen.oneOf(biasedValues)
  }

  private val refundBiasGen: Gen[BigDecimal] = {
    val biasedValues = (85 to 99).map(i => BigDecimal(i) / BigDecimal(100)) ++
      List.fill(5)(BigDecimal(1))
    Gen.oneOf(biasedValues)
  }


  private def yearDataGen(year: Int): Gen[(List[ChargeDetails], List[PaymentHistoryDetails])] = {
    val now = LocalDate.now()
    for {
      statementMonth <- Gen.choose(1, 12)
      recentStatementMonth <- Gen.choose(2, 6)
      statementDay <- Gen.choose(1, 28)
      numberOfStatements <- Gen.choose(1, 3)
      statementDate =
        if now.getYear.intValue == year then
          LocalDate.of(
            year,
            now.minusMonths(recentStatementMonth).getMonthValue,
            now.getDayOfMonth - statementDay
          )
        else LocalDate.of(year, statementMonth, statementDay)
      payments <- Gen.listOfN(numberOfStatements, paymentHistoryGen(statementDate))
      charges = payments.map(chargeDetailsGen(statementDate, _))  //list gen charge
    } yield (chargeList, payments)
  }

  private def paymentHistoryGen(statementDate: LocalDate): Gen[PaymentHistoryDetails] = {
    for {
      paymentDate <- dateInRange(statementDate, statementDate.plusDays(59))
      amount <- amountGen
      method <- paymentMethodGen
      reference <- chargeRefGen
      processedDate <- Gen.option(dateInRange(paymentDate, paymentDate.plusDays(5)))
      allocationRef <- chargeRefGen.map(List(_))
    } yield PaymentHistoryDetails(
      paymentAmount = roundValue(amount),
      paymentReference = reference,
      paymentMethod = Some(method),
      paymentDate = paymentDate,
      processedDate = processedDate,
      allocationReference = allocationRef
    )
  }

  def amendmentGen(
      processDate: LocalDate,
      amount: BigDecimal,
      payment: PaymentHistoryDetails
  ): Gen[Set[Amendment]] = {
    Gen.const(
      Set(
        Amendment(
          amendmentDate = processDate,
          amendmentAmount = amount,
          amendmentReason = "payment",
          updatedChargeAmount = None,
          paymentMethod = payment.paymentMethod,
          paymentDate = Some(payment.paymentDate)
        )
      )
    )
  }

  private def creditAmendmentGen(charge: ChargeDetails, amount: BigDecimal): Gen[ChargeDetails] = {
    for {
      amendmentDate <- dateInRange(today.minusDays(20), today)
    } yield {
      val remainingBalance = charge.chargeAmount - amount
      val amendments = List(
        Amendment(
          amendmentDate = amendmentDate,
          amendmentAmount = amount,
          amendmentReason = "Credit applied from overpayment",
          updatedChargeAmount = Some(remainingBalance),
          paymentMethod = None,
          paymentDate = None
        )
      )
      charge.copy(amendments = amendments, outstandingAmount = remainingBalance)
    }
  }

  private def calculateInterestDue(
      dueDate: LocalDate,
      outstandingAmount: BigDecimal
  ): Option[BigDecimal] = {
    val monthsBetween = ChronoUnit.MONTHS.between(dueDate.plusMonths(1), today)
    if (monthsBetween > 0) {
      Some(
        roundValue(
          outstandingAmount * (BigDecimal(monthsBetween) / BigDecimal(12)) * BigDecimal("0.05")
        )
      )
    } else None
  }

  private def chargeDetailsGen(
      statementDate: LocalDate,
      payment: PaymentHistoryDetails
  ): Gen[ChargeDetails] = {
    for {
      chargeType <- chargeTypeGen
      multiplier <- biasedMultiplierGen
      chargeReference <- chargeRefGen
      chargeId = payment.allocationReference.headOption.getOrElse(chargeReference)
    } yield {
      val dueDate = statementDate.plusMonths(2)
      val taxYear = s"${statementDate.getYear}-${statementDate.getYear + 1}"
      val isNotRecentStatement = statementDate.isBefore(today.minusDays(45))
      val chargeAmount = roundValue(payment.paymentAmount * multiplier)
      val processDate =
        payment.processedDate.getOrElse(dueDate.minusDays(scala.util.Random.nextInt(10)))

      val amendmentAmount =
        if (chargeAmount < payment.paymentAmount) chargeAmount else payment.paymentAmount
      val outstandingAmount = roundValue(
        if (isNotRecentStatement) chargeAmount - amendmentAmount else chargeAmount
      )
      val interest = calculateInterestDue(dueDate, outstandingAmount)
      val hasInterest = chargeAmount > amendmentAmount && dueDate.isBefore(today)

      ChargeDetails(
        chargeId = chargeId,
        creationDate = statementDate,
        chargeType = chargeType,
        chargeAmount = chargeAmount,
        taxYear = taxYear,
        dueDate = dueDate,
        amendments = if (isNotRecentStatement) {
            List(
              Amendment(
                amendmentDate = processDate,
                amendmentAmount = amendmentAmount,
                amendmentReason = "payment",
                updatedChargeAmount = None,
                paymentMethod = payment.paymentMethod,
                paymentDate = Some(payment.paymentDate)
              )
            )
        } else List.empty[Amendment],
        outstandingAmount = outstandingAmount + interest.getOrElse(BigDecimal(0)),
        outstandingInterestDue = if (hasInterest) interest else None,
        accruingInterest = if (hasInterest) interest else None,
        accruingInterestPeriod = if (hasInterest) {
          interest.map(_ => AccruingInterestPeriod(dueDate.plusMonths(1), today))
        } else None,
        accruingInterestRate = if (hasInterest) Some(BigDecimal("0.05")) else None
      )
    }
  }

  private def refundDetailsGen(
      payments: Set[PaymentHistoryDetails],
      charges: Set[ChargeDetails]
  ): Gen[Set[RefundDetails]] = {
    val remainingBalance = payments.map(_.paymentAmount).sum - charges.map(_.chargeAmount).sum

    if (remainingBalance <= BigDecimal(0)) {
      Gen.const(Set.empty[RefundDetails])
    } else {
      for {
        randomDayOfRefund <- Gen.choose(1, 45)
        randomProcessDays <- Gen.choose(1, 45)
        refundBias <- refundBiasGen
      } yield {
        val requestDate = payments.map(_.paymentDate).max.plusDays(randomDayOfRefund)
        val processedDate = requestDate.plusDays(randomProcessDays)
        val randomRefundAmount = roundValue(remainingBalance * refundBias)

        val interest =
          (BigDecimal(ChronoUnit.DAYS.between(requestDate, processedDate)) / BigDecimal(28)) *
            BigDecimal("0.001") * randomRefundAmount

        val overpaymentDates = findOverpaymentDates(payments, charges)
        val refundMethod =
          scala.util.Random.shuffle(List("bank transfer", "card", "direct debit", "cheque")).head

        Set(
          RefundDetails(
            refundDate = processedDate,
            refundMethod = Some(refundMethod),
            refundRequestDate = Some(requestDate),
            refundRequestAmount = randomRefundAmount,
            refundDescription = Some(
              s"Refund for overpayment(s) made on ${overpaymentDates.map(_.format(dateFormatter)).mkString(" and ")}"
            ),
            interestAddedToRefund = Some(roundValue(interest)),
            totalRefundAmount = randomRefundAmount + interest,
            refundStatus = if (processedDate.isAfter(today)) Some("pending") else Some("accepted")
          )
        )
      }
    }
  }

  private def allocateCreditToFutureOrOverdueCharges(
      charges: Set[ChargeDetails],
      payments: Set[PaymentHistoryDetails],
      refunds: Set[RefundDetails]
  ): Gen[(Set[ChargeDetails], BigDecimal)] = {
    val totalCreditAvailable = roundValue(
      payments.map(_.paymentAmount).sum -
        charges.map(_.chargeAmount).sum -
        refunds.map(_.refundRequestAmount).sum
    )

    if (totalCreditAvailable <= BigDecimal(0)) {
      Gen.const((charges, BigDecimal(0)))
    } else {
      getOverdueOrFutureCharges(charges) match {
        case Some(eligibleCharge) =>
          val creditToApply = totalCreditAvailable.min(eligibleCharge.outstandingAmount)
          val creditLeftAfterAssignment = totalCreditAvailable - creditToApply

          creditAmendmentGen(eligibleCharge, creditToApply).map { updatedCharge =>
            val updatedCharges = charges - eligibleCharge + updatedCharge
            (updatedCharges, creditLeftAfterAssignment)
          }
        case None =>
          Gen.const((charges, BigDecimal(0)))
      }
    }
  }

  private def balanceDetailsGen(
      charges: Set[ChargeDetails],
      creditLeft: BigDecimal
  ): Gen[BalanceDetails] = {
    Gen.const {
      val allOverDueCharges = charges.filter(_.dueDate.isBefore(today))
      val overDueChargesWithAnOutstandingAmount =
        allOverDueCharges.filter(_.outstandingAmount > BigDecimal(0))

      val getCodedOut = overDueChargesWithAnOutstandingAmount.headOption.map { overdueCharge =>
        Set(
          CodedOutDetail(
            totalAmount = overdueCharge.outstandingAmount,
            effectiveStartDate = overdueCharge.dueDate,
            effectiveEndDate = overdueCharge.dueDate.plusYears(1).withMonth(4).withDayOfMonth(5)
          )
        )
      }

      val totalOverDueBalance = overDueChargesWithAnOutstandingAmount.map(_.outstandingAmount).sum -
        getCodedOut.map(_.map(_.totalAmount).sum).getOrElse(BigDecimal(0))

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
  }


  def hipResponseGen(fromDate: LocalDate, toDate: LocalDate): Gen[HipResponse] = {
    for {
      yearDataList <- Gen.sequence((fromDate.getYear to toDate.getYear).map(yearDataGen))
      yearData = yearDataList.asScala.toList
      allCharges = yearData.flatMap(_._1)
      allPaymentHistory = yearData.flatMap(_._2).toSet
      allRefunds <- refundDetailsGen(allPaymentHistory, allCharges)
      chargesWithRefundAllocated <- allocateCreditToFutureOrOverdueCharges(
        allCharges,
        allPaymentHistory,
        allRefunds
      )
      balanceDetails <- balanceDetailsGen(
        chargesWithRefundAllocated._1,
        chargesWithRefundAllocated._2
      )
    } yield HipResponse(
      balanceDetails = balanceDetails,
      chargeDetails = chargesWithRefundAllocated._1,
      refundDetails = allRefunds,
      paymentHistoryDetails = allPaymentHistory
    )
  }

  private def getOverdueOrFutureCharges(charges: Set[ChargeDetails]): Option[ChargeDetails] = {
    val overdueChargesWithOutstanding = charges.filter { charge =>
      charge.dueDate.isBefore(today) && charge.outstandingAmount > BigDecimal(0)
    }

    if (overdueChargesWithOutstanding.nonEmpty) {
      overdueChargesWithOutstanding.minByOption(_.dueDate)
    } else {
      charges
        .filter(charge => charge.amendments.forall(_.isEmpty))
        .minByOption(_.dueDate)
    }
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

  private def getTheEarliestDueDate(charges: Set[ChargeDetails]): Option[LocalDate] = {
    if (charges.nonEmpty && charges.map(_.outstandingAmount).sum > BigDecimal(0))
      Some(charges.map(_.dueDate).min)
    else None
  }

  private def roundValue(num: BigDecimal): BigDecimal = {
    num.setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }
}
