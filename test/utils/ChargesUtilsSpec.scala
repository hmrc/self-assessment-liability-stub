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

import models.ChargeDetails
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class ChargesUtilsSpec extends AnyWordSpec with Matchers {
  val fromDate: LocalDate = LocalDate.of(2023, 1, 1)
  val toDate: LocalDate = LocalDate.of(2023, 12, 31)
  val today: LocalDate = LocalDate.now()

  "generateCharge method" should {

    "return an empty list when there are no payments" in {
      val result = ChargesUtils.generateCharge(Nil)
      result shouldBe Nil
    }
    "generate a charge for a single payment" in {
      val payments = PaymentUtils.generatePaymentHistory(today.minusDays(10))

      val result = ChargesUtils.generateCharge(List(payments))
      result.size shouldBe 1
      val charge = result.head

      charge.creationDate.isAfter(payments.paymentDate.minusDays(16)) shouldBe true
      charge.creationDate.isBefore(payments.paymentDate.minusDays(9)) shouldBe true

      charge.outstandingAmount shouldEqual charge.chargeAmount

      charge.chargeAmount shouldBe >=(BigDecimal(0))
      charge.outstandingAmount shouldBe >=(BigDecimal(0))
    }
    "generate a charge for multiple payments" in {

      val payments = List(
        PaymentUtils.generatePaymentHistory(today.minusDays(5)),
        PaymentUtils.generatePaymentHistory(today.minusDays(50)),
        PaymentUtils.generatePaymentHistory(today.minusDays(1))
      )

      val result = ChargesUtils.generateCharge(payments)
      result.size shouldBe payments.size
    }
    "apply not recent statement logic where paymentDate before today - 45" in {
      val payments =
        PaymentUtils.generatePaymentHistory(today.minusDays(46))

      val result = ChargesUtils.generateCharge(List(payments))
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
      val payments = PaymentUtils.generatePaymentHistory(today.minusDays(45))

      val result = ChargesUtils.generateCharge(List(payments))
      val charge = result.head

      charge.outstandingAmount shouldEqual charge.chargeAmount
    }

    "use paymentDate + 6 when processedDate is missing" in {
      val payments = PaymentUtils.generatePaymentHistory(today.minusDays(46))
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
      val payments = PaymentUtils.generatePaymentHistory(today.minusDays(10))
      val paymentWithoutRef = payments.copy(allocationReference = Nil)

      val result = ChargesUtils.generateCharge(List(paymentWithoutRef))
      val charge = result.head

      charge.chargeId should not be empty
    }
  }
}
