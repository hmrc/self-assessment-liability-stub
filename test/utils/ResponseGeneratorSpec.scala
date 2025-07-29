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

import models.HipResponse

import java.time.LocalDate
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ResponseGeneratorSpec extends AnyWordSpec with Matchers {
  "Generate JSON response" should {
    "Get the correct tax year" in {
      val date1: LocalDate = LocalDate.parse("2024-07-01")
      val date2: LocalDate = LocalDate.parse("2025-01-01")
      val date3: LocalDate = LocalDate.parse("2025-04-06")
      val date4: LocalDate = LocalDate.parse("2025-07-01")
      ResponseGenerator.getTaxYear(date1) shouldBe 2024
      ResponseGenerator.getTaxYear(date2) shouldBe 2024
      ResponseGenerator.getTaxYear(date3) shouldBe 2025
      ResponseGenerator.getTaxYear(date4) shouldBe 2025
    }

    "Generate a response from a given year" in {
      val hipResponse: HipResponse = ResponseGenerator.generateResponse(2023, 2024)

//      hipResponse.chargeDetails.foreach(f =>
//        f._1.toString shouldBe f._2.balanceDetails.payableDueDate.split("-")(0)
//      )
      print(hipResponse)
    }
  }
}
