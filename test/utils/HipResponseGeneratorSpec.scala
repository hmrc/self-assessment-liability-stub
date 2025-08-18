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

import models.{ChargeDetails, HipResponse, PaymentHistoryDetails, RefundDetails}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class HipResponseGeneratorSpec extends AnyWordSpec with Matchers {
  val fromDate: LocalDate = LocalDate.of(2023, 1, 1)
  val toDate: LocalDate = LocalDate.of(2023, 12, 31)
  val today: LocalDate = LocalDate.now()

  private def generateSamples(
      fromDate: LocalDate,
      toDate: LocalDate,
      count: Int = 5
  ): List[HipResponse] = {
    (1 to count).flatMap(_ => HipResponseGenerator.hipResponseGen(fromDate, toDate).sample).toList
  }

  "HipResponseGenerator" should {
    "generate a response from given date range" in {
      val samples = generateSamples(fromDate, toDate)
      samples should not be empty

      samples.foreach { hipResponse =>
        hipResponse.balanceDetails should not be null
        hipResponse.chargeDetails shouldBe defined
        hipResponse.refundDetails shouldBe defined
        hipResponse.paymentHistoryDetails shouldBe defined

        hipResponse.chargeDetails.foreach { charges =>
          charges.size should be <= 9
          charges.foreach { charge =>
            charge.creationDate.getYear should (be >= 2023 and be <= 2023)

            hipResponse.paymentHistoryDetails.foreach { payments =>
              payments.size should be <= 9
              payments.foreach { payment =>
                payment.paymentDate.getYear should (be >= 2023 and be <= 2023)
                payment.paymentDate should be >= charge.creationDate
              }
            }
          }
        }
      }
    }

    "make sure refund is generated if there is a surplus in payments" in {
      val samples = generateSamples(fromDate, toDate)

      samples.foreach { response =>
        val charges = response.chargeDetails.getOrElse(Set.empty[ChargeDetails])
        val payments = response.paymentHistoryDetails.getOrElse(Set.empty[PaymentHistoryDetails])
        val refunds = response.refundDetails.getOrElse(Set.empty[RefundDetails])

        val chargeIds = charges.map(_.chargeId)
        payments.foreach { payment =>
          payment.allocationReference.get.foreach { ref =>
            chargeIds should contain(ref)
          }
        }

        val totalPayments = payments.map(_.paymentAmount).sum
        val totalCharges = charges.map(_.chargeAmount).sum

        if (totalPayments > totalCharges) {
          refunds should not be empty
        }
      }
    }

    "correctly identify charges by due date" in {
      val samples = generateSamples(fromDate, toDate)

      samples.foreach { response =>
        val charges = response.chargeDetails.getOrElse(Set.empty[ChargeDetails])

        val overdueCharges = charges.filter(_.dueDate.isBefore(today))
        val payableCharges = charges.filter { charge =>
          charge.dueDate.isBefore(today.plusDays(29)) && charge.dueDate.isAfter(today)
        }
        val pendingCharges = charges.filter(_.dueDate.isAfter(today.plusDays(30)))

        val allCharges = overdueCharges ++ payableCharges ++ pendingCharges
        allCharges.size shouldBe charges.size

        payableCharges.foreach { charge =>
          charge.dueDate should be > today
          charge.dueDate should be < today.plusDays(29)
        }

        overdueCharges.foreach { charge =>
          charge.dueDate should be < today
        }

        pendingCharges.foreach { charge =>
          charge.dueDate should be > today.plusDays(30)
        }
      }
    }

    "generate a coded out item if there is at least one overdue charge" in {
      val samples = generateSamples(fromDate, toDate)

      samples.foreach { response =>
        val balanceDetails = response.balanceDetails
        val charges = response.chargeDetails.get

        val overdueCharges = charges.filter(_.dueDate.isBefore(today))
        val overdueChargesWithOutstanding = overdueCharges.filter(_.outstandingAmount > 0)

        if (overdueChargesWithOutstanding.nonEmpty) {
          balanceDetails.codedOutDetail shouldBe defined
          val codedOutDetail = balanceDetails.codedOutDetail.get.head

          codedOutDetail.totalAmount should be > BigDecimal(0.0)
          codedOutDetail.effectiveStartDate should not be null
          codedOutDetail.effectiveEndDate should not be null
          codedOutDetail.effectiveEndDate should be > codedOutDetail.effectiveStartDate

          val expectedOverdueBalance =
            overdueChargesWithOutstanding.map(_.outstandingAmount).sum - codedOutDetail.totalAmount
          balanceDetails.totalOverdueBalance shouldBe expectedOverdueBalance
        }
      }
    }

    "total balance calculations add up" in {
      val samples = generateSamples(fromDate, toDate)

      samples.foreach { response =>
        val balanceDetails = response.balanceDetails

        val calculatedTotal = balanceDetails.totalOverdueBalance +
          balanceDetails.totalPayableBalance +
          balanceDetails.totalPendingBalance

        balanceDetails.totalBalance should equal(calculatedTotal)
      }
    }

  }
}
