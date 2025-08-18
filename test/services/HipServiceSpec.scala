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

package services

import models.HipResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HipServiceSpec extends AnyWordSpec with Matchers {

  val hipService = new HipService()

  "HipService" should {

    "generate a valid response with valid date strings" in {
      val fromDate = "2023-01-01"
      val toDate = "2023-12-31"

      val response = hipService.generateResponse(fromDate, toDate)

      response shouldBe a[HipResponse]
      response.balanceDetails should not be null
      response.chargeDetails shouldBe defined
      response.refundDetails shouldBe defined
      response.paymentHistoryDetails shouldBe defined
    }
  }
}
