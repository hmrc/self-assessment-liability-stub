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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.*
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class MtdItIdAuthFunctionTest extends AnyFunSuite with MockitoSugar with EitherValues {

  val mockAuthConnector = mock[AuthConnector]

  type RetrievalsType = Enrolments

  val requiredRetrievals: Retrieval[Enrolments] =
    Retrievals.allEnrolments

  val mtdItIdEnrolment: Enrolments = Enrolments(
    Set(
      Enrolment(
        MtdItIdAuthFunction.Mtd_Enrolment_Key,
        Seq(EnrolmentIdentifier(MtdItIdAuthFunction.Mtd_Identifier, "1234abcd")),
        "Activated",
        None
      )
    )
  )

  test("Extract mtdItid from request") {
    when(
      mockAuthConnector.authorise[RetrievalsType](
        ArgumentMatchers.eq(EmptyPredicate),
        ArgumentMatchers.eq(requiredRetrievals)
      )(
        any[HeaderCarrier](),
        any[ExecutionContext]()
      )
    )
      .thenReturn(
        Future.successful(mtdItIdEnrolment)
      )

    val classUnderTest = new MtdItIdAuthFunction(mockAuthConnector)
    val request = FakeRequest("GET", "/")
    val result = Await.result(classUnderTest.refine(request), 5.seconds)
    result.isRight shouldBe true
    result.value.mtdItId shouldEqual Option("1234abcd")
  }

  test("Extract nothing is no enrolment exists") {
    when(
      mockAuthConnector.authorise[RetrievalsType](
        ArgumentMatchers.eq(EmptyPredicate),
        ArgumentMatchers.eq(requiredRetrievals)
      )(
        any[HeaderCarrier](),
        any[ExecutionContext]()
      )
    )
      .thenReturn(
        Future.successful(Enrolments(Set.empty[Enrolment]))
      )

    val classUnderTest = new MtdItIdAuthFunction(mockAuthConnector)
    val request = FakeRequest("GET", "/")
    val result = Await.result(classUnderTest.refine(request), 5.seconds)
    result.isRight shouldBe true
    result.value.mtdItId shouldBe None
  }
}
