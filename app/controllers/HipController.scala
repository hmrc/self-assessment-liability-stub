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

package controllers

import models.{AccruingInterestDateRange, Amendments, BalanceDetails, ChargeDetails, CodedOutDetail, HipResponse, PaymentHistoryDetails, RefundDetails}
import utils.constants.RequestResponseConstants.*
import utils.date.DateParser.StringParser
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.text.{ParseException, SimpleDateFormat}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.Random

@Singleton()
class HipController @Inject() (cc: ControllerComponents) extends BackendController(cc) {
  def getSelfAssessmentData(utr: String, dateFrom: String, dateTo: String): Action[AnyContent] = Action.async {
    implicit request =>
      if (utr.equalsIgnoreCase(badUtrHipInvalidCorrelationId)) {
        Future.successful(
          BadRequest(
            Json.obj("message" -> "Submission has not passed validation. Invalid Correlation Id.")
          )
        )
      } else if (utr.equalsIgnoreCase(badUtrHipUnauthorised)) {
        Future.successful(
          Unauthorized(Json.obj("message" -> "Invalid basic authentication credentials."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipForbidden)) {
        Future.successful(
          Forbidden(
            Json.obj("message" -> "User does not have authority to retrieve requested record.")
          )
        )
      } else if (utr.equalsIgnoreCase(badUtrHipUtrNotFound)) {
        Future.successful(
          NotFound(Json.obj("message" -> "Identifier not found."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipUtrInvalid)) {
        Future.successful(
          UnprocessableEntity(Json.obj("message" -> "Invalid utr entered."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipServerError)) {
        Future.successful(
          InternalServerError(Json.obj("message" -> "Internal server error."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipExternalServiceError)) {
        Future.successful(
          BadGateway(Json.obj("message" -> "Error communicating with external service."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipServiceUnavailable)) {
        Future.successful(
          ServiceUnavailable(Json.obj("message" -> "Service unavailable"))
        )
      } else {
        try {
          val dateFromDate: Date = dateFrom.parseDate
          // TODO: Do something with the dateFromDate.
        } catch
          case pe: ParseException => //TODO: Return "else".

        
        if (dateFrom.equals("2025-04-06")) {
          Future.successful(Ok(Json.toJson(validHipJsonResponse2025)))
        } else if (dateFrom.equals("2024-04-06")) {
          Future.successful(Ok(Json.toJson(validHipJsonResponse2024)))
        } else {
          Future.successful(Ok(Json.toJson(validHipJsonResponse2023)))
        }
      }
  }
}

object HipController {

    private val random = new Random()
    private val chargeTypes = List("ITSA", "Penalty", "PAYE")
    private val amendmentTypes = List("payment", "credit", "adjustment")
    private val paymentMethods = List("bank transfer", "card", "direct debit", "cheque")
    private val refundStatuses = List("processed", "pending", "rejected")


    def generateDocumentsFromYear(fromDate: LocalDate): Map[Int, HipResponse] = {
      val startYear = fromDate.getYear
      val currentYear = LocalDate.now().getYear

      (startYear to currentYear).map { year =>
        year -> generateDocumentForYear(year)
      }.toMap
    }
  
    private def generateDocumentForYear(year: Int): HipResponse = {
      val numCharges = random.nextInt(2) + 1
      val charges = (1 to numCharges).map(_ => generateCharge(year)).toSet

      val balanceDetails = generateBalanceDetails(year, charges)
      val refunds = generateRefunds(year)
      val paymentHistory = generatePaymentHistory(year, charges)

      HipResponse(balanceDetails, Some(charges), Some(refunds), Some(paymentHistory))
    }

    private def generateBalanceDetails(year: Int, charges: Set[ChargeDetails]): BalanceDetails = {
      val totalOutstanding = charges.map(_.outstandingAmount).sum
      val totalChargeAmount = charges.map(_.chargeAmount).sum
      val totalCodedOut = charges.flatMap(_.codedOutDetail.getOrElse(Set.empty)).flatMap(_.amount).sum

      BalanceDetails(
        totalOverdueBalance = if (year < LocalDate.now().getYear) totalOutstanding else 0.00,
        totalPayableBalance = totalOutstanding * random.nextDouble(),
        payableDueDate = generateDateInYear(year, isEndOfYear = true),
        totalPendingBalance = totalOutstanding + random.nextInt(2000),
        pendingDueDate = generateFutureDate(year),
        totalBalance = totalChargeAmount,
        totalCodedOut = totalCodedOut,
        totalCreditAvailable = random.nextInt(1000)
      )
    }

    private def generateCharge(year: Int): ChargeDetails = {
      val chargeAmount = random.nextInt(5000) + 500
      val outstandingAmount = chargeAmount * random.nextDouble()
      val chargeType = chargeTypes(random.nextInt(chargeTypes.length))

      val amendments = if (random.nextBoolean()) {
        (1 to random.nextInt(3) + 1).map(_ => generateAmendment(year, chargeAmount)).toSet
      } else Set.empty

      val codedOut = if (random.nextBoolean() && year < LocalDate.now().getYear) {
        Set(generateCodedOutDetail(year, chargeType))
      } else Set.empty
      val interestStartDate = generateDateInYear(year + 1)
      val interestEndDate = generateDateInYear(year + 1, isEndOfYear = true)
      val isLate = random.nextBoolean()
      val interestAmount =  Some(random.nextInt(200).toDouble)
      ChargeDetails(
        chargeId = generateChargeId(),
        creationDate = generateDateInYear(year),
        chargeType = chargeType,
        chargeAmount = chargeAmount,
        outstandingAmount = outstandingAmount,
        taxYear = s"${year}-${year + 1}",
        dueDate = generateDateInYear(year + 1),
        interestAmountDue = if isLate then interestAmount else None, 
        accruingInterest = if isLate then interestAmount else None, 
        accruingInterestDateRange = if isLate then Some(AccruingInterestDateRange(interestStartDate,interestEndDate)) else None,
        accruingInterestRate = if isLate then Some(0.05) else None,
        amendments = Some(amendments),
        codedOutDetail = Some(codedOut)
      )
    }

    private def generateAmendment(year: Int, maxAmount: Double): Amendments = {
    val amendmentReason = amendmentTypes(random.nextInt(amendmentTypes.length))
    val amendmentAmount = maxAmount * random.nextDouble()

    Amendments(
      amendmentDate = generateDateInYear(year),
      amendmentAmount = amendmentAmount,
      amendmentReason = amendmentReason,
      newChargeBalance = Some(maxAmount - amendmentAmount),
      paymentMethod = if (amendmentReason == "payment") Some(paymentMethods(random.nextInt(paymentMethods.length))) else None,
      paymentDate = if (amendmentReason == "payment") Some(generateDateInYear(year)) else None
    )
  }

    private def generateCodedOutDetail(year: Int, chargeType: String): CodedOutDetail = {
      CodedOutDetail(
        amount = Some(random.nextInt(500) + 100),
        effectiveDate = Some(generateDateInYear(year)),
        taxYear = Some(s"${year}-${year + 1}"),
        effectiveTaxYear = Some(s"${year + 1}-${year + 2}")
      )
    }

    private def generateRefunds(year: Int): Set[RefundDetails] = {
      if (random.nextBoolean() && year < LocalDate.now().getYear) {
        val numRefunds = random.nextInt(2) + 1
        (1 to numRefunds).map(_ => {
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
        }).toSet
      } else Set.empty
    }

    private def generatePaymentHistory(year: Int, charges: Set[ChargeDetails]): Set[PaymentHistoryDetails] = {
    charges.flatMap { charge =>
      charge.amendments.getOrElse(Set.empty)
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
        case 2 => if (year % 4 == 0) 29 else 28
        case 4 | 6 | 9 | 11 => 30
        case _ => 31
      }
      val day = random.nextInt(maxDay) + 1
      LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private def generateFutureDate(baseYear: Int): String = {
      val futureYear = baseYear + random.nextInt(2) + 1
      generateDateInYear(futureYear)
    }
  }

