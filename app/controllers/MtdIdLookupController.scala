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

import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.constants.RequestResponseConstants.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class MtdIdLookupController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  def getMtdId(nino: String): Action[AnyContent] = Action.async { implicit request =>
    if (nino.equalsIgnoreCase(invalidNinoBadRequest)) {
      val errorResponse = ErrorResponse(
        origin = "HIP",
        response = List(FailureDetail("BAD_REQUEST", "Invalid request format or parameters."))
      )
      Future.successful(BadRequest(Json.toJson(errorResponse)))
    } else if (nino.equalsIgnoreCase(invalidNinoServerError)) {
      val errorResponse = ErrorResponse(
        origin = "HIP",
        response = List(FailureDetail("INTERNAL_SERVER_ERROR", "Service currently unavailable."))
      )
      Future.successful(InternalServerError(Json.toJson(errorResponse)))
    } else {
      Future.successful(Ok(Json.obj("mtdbsa" -> validMtditid)))
    }
  }
}

case class ErrorResponse(origin: String, response: List[FailureDetail])
case class FailureDetail(`type`: String, reason: String)

object ErrorResponse {
  implicit val failureDetailFormat: Format[FailureDetail] = Json.format[FailureDetail]
  implicit val errorResponseFormat: Format[ErrorResponse] = Json.format[ErrorResponse]
}
