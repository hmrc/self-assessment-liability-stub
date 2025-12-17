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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import controllers.MtdItIdAuthFunction.{Mtd_Enrolment_Key, Mtd_Identifier}
import play.api.Logging
import play.api.mvc.{ActionTransformer, Request, WrappedRequest}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

case class RequestWithMtdId[A](mtdItId: Option[String], request: Request[A])
    extends WrappedRequest[A](request)

@ImplementedBy(classOf[MtdItIdAuthFunction])
trait MtdItIdAuthTransformer extends ActionTransformer[Request, RequestWithMtdId]

@Singleton
class MtdItIdAuthFunction @Inject() (val authConnector: AuthConnector)(implicit
    val executionContext: ExecutionContext
) extends MtdItIdAuthTransformer
    with AuthorisedFunctions
    with Logging {

  override protected def transform[A](request: Request[A]): Future[RequestWithMtdId[A]] = {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    logger.info(s"auth header is ${headerCarrier.authorization}")
    authorised().retrieve(allEnrolments) { enrolments =>
      val mtditid = enrolments.getEnrolment(Mtd_Enrolment_Key)
      Future.successful(
        RequestWithMtdId(mtditid.flatMap(_.getIdentifier(Mtd_Identifier).map(_.value)), request)
      )
    }
  }

}

object MtdItIdAuthFunction {
  val Mtd_Enrolment_Key = "HMRC-MTD-IT"
  val Mtd_Identifier = "MTDITID"
}
