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
import utils.ResponseGenerator.{random, randomPaymentMethod, today}

import java.time.LocalDate

object paymentUtils {

  def generatePaymentHistory(paymentDate: LocalDate): PaymentHistoryDetails = {
    val paymentAmount = BigDecimal(random.between(500, 50000))
    val paymentDateFilter = paymentDate.plusDays(random.nextInt(6))
    PaymentHistoryDetails(
      paymentAmount = paymentAmount,
      paymentReference = ResponseGenerator.generateId(),
      paymentMethod = Some(randomPaymentMethod),
      paymentDate = paymentDate,
      processedDate = Some(if (paymentDateFilter.isAfter(today)) today else paymentDateFilter),
      allocationReference = List(ResponseGenerator.generateId())
    )
  }
}
