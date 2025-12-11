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

import models.PaymentHistoryDetails
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class ChargesUtilsSpec extends AnyWordSpec with Matchers {
  val fromDate: LocalDate = LocalDate.of(2023, 1, 1)
  val toDate: LocalDate = LocalDate.of(2023, 12, 31)
  val today: LocalDate = LocalDate.now()
  val payments = PaymentHistoryDetails(
    paymentAmount = BigDecimal(100.00),
    paymentReference = Some("payment-123"),
    paymentMethod = Some("bank transfer"),
    paymentDate = today.minusDays(46),
    processedDate = Some(today.minusDays(40)),
    allocationReference = List("charge-123")
  )

  "generateCharge method" should {

    "generate a charge for a single payment" in {
      val paymentsValid =
        payments.copy(paymentDate = today.minusDays(6), processedDate = Some(today))

      val result = ChargesUtils.generateCharge(List(paymentsValid))
      result.size shouldBe 1
      val charge = result.head

      charge.creationDate.isAfter(paymentsValid.paymentDate.minusDays(16)) shouldBe true
      charge.creationDate.isBefore(paymentsValid.paymentDate.minusDays(9)) shouldBe true

      charge.outstandingAmount shouldEqual charge.chargeAmount

      charge.chargeAmount shouldBe >=(BigDecimal(0))
      charge.outstandingAmount shouldBe >=(BigDecimal(0))
    }
    "generate a charge for multiple payments" in {

      val payments = List(
        PaymentHistoryDetails(
          paymentAmount = BigDecimal(100.00),
          paymentReference = Some("payment-123"),
          paymentMethod = Some("bank transfer"),
          paymentDate = today.minusDays(5),
          processedDate = Some(today),
          allocationReference = List("charge-123")
        ),
        PaymentHistoryDetails(
          paymentAmount = BigDecimal(50.00),
          paymentReference = Some("payment-456"),
          paymentMethod = Some("bank transfer"),
          paymentDate = today.minusDays(50),
          processedDate = Some(today.minusDays(44)),
          allocationReference = List("charge-456")
        ),
        PaymentHistoryDetails(
          paymentAmount = BigDecimal(500.00),
          paymentReference = Some("payment-789"),
          paymentMethod = Some("bank transfer"),
          paymentDate = today.minusDays(1),
          processedDate = Some(today),
          allocationReference = List("charge-789")
        )
      )

      val result = ChargesUtils.generateCharge(payments)
      result.size shouldBe payments.size

      val (recent1, notRecent, recent2) = (result.head, result(1), result(2))

      recent1.amendments shouldBe empty
      recent2.amendments shouldBe empty
      notRecent.amendments.nonEmpty shouldBe true

      recent1.chargeId shouldEqual payments.head.allocationReference.head
      notRecent.chargeId shouldEqual payments(1).allocationReference.head
      recent2.chargeId shouldEqual payments(2).allocationReference.head

      result.foreach { charge =>
        charge.chargeAmount shouldBe >=(BigDecimal(0))
        charge.outstandingAmount shouldBe >=(BigDecimal(0))
      }
    }
    "apply not recent statement logic where paymentDate before today - 45" in {
      val paymentsNotRecent = payments.copy(paymentDate = today.minusDays(46))

      val result = ChargesUtils.generateCharge(List(paymentsNotRecent))
      val charge = result.head

      val amendmentAmount =
        if (charge.chargeAmount < payments.paymentAmount) charge.chargeAmount
        else payments.paymentAmount

      charge.outstandingAmount shouldBe >=(BigDecimal(0))
      charge.outstandingAmount shouldBe <=(charge.chargeAmount)

      if (charge.chargeAmount > 0 && amendmentAmount > 0) {
        charge.outstandingAmount shouldBe <(charge.chargeAmount)
      }
    }
    "treat paymentDate exactly today - 45 as recent (edge case)" in {
      val paymentsEdgeCase = payments.copy(paymentDate = today.minusDays(45))

      val result = ChargesUtils.generateCharge(List(paymentsEdgeCase))
      val charge = result.head

      charge.outstandingAmount shouldEqual charge.chargeAmount
    }

    "use paymentDate + 6 when processedDate is missing" in {
      val paymentWithoutProcessed = payments.copy(processedDate = None)

      val result = ChargesUtils.generateCharge(List(paymentWithoutProcessed))
      val charge = result.head

      charge.amendments.nonEmpty shouldBe true

      val amendDate =
        charge.amendments.headOption
          .map(_.amendmentDate)
          .getOrElse(fail("Expected at least one amendment"))

      amendDate shouldEqual paymentWithoutProcessed.paymentDate.plusDays(6)
    }
    "generate chargeId randomly when allocationReference is empty" in {
      val paymentsRandomChargeId = payments.copy(allocationReference = List.empty)

      val result = ChargesUtils.generateCharge(List(paymentsRandomChargeId))
      val charge = result.head

      charge.chargeId should not be empty
    }
  }
}
