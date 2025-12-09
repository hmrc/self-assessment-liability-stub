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

import models.{FailureDetail, HipErrorResponse, ResponseWrapper}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.constants.RequestResponseConstants.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class MtdIdLookupController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  private val errorCodeMap = Map(
    "001" -> "REGIME missing or invalid",
    "006" -> "Subscription data not found",
    "007" -> "Your request cannot be processed, please contact the help line",
    "008" -> "ID not found"
  )

  def getMtdId(nino: String): Action[AnyContent] = Action.async { implicit request =>
    if (nino.equalsIgnoreCase(invalidNinoBadRequest)) {
      val errorResponse = HipErrorResponse(
        origin = "HIP",
        response = ResponseWrapper(failures =
          List(FailureDetail("BAD_REQUEST", "Invalid request format or parameters."))
        )
      )

      Future.successful(BadRequest(Json.toJson(errorResponse)))
    } else if (nino.equalsIgnoreCase(invalidNinoServiceUnavailable)) {
      val errorResponse = HipErrorResponse(
        origin = "HIP",
        response = ResponseWrapper(failures =
          List(FailureDetail("SERVICE_UNAVAILABLE", "Service is currently unavailable."))
        )
      )
      Future.successful(ServiceUnavailable(Json.toJson(errorResponse)))
    } else if (nino.equalsIgnoreCase(invalidNinoServerError)) {
      val errorResponse = HipErrorResponse(
        origin = "HIP",
        response = ResponseWrapper(failures =
          List(FailureDetail("INTERNAL_SERVER_ERROR", "Internal server error."))
        )
      )
      Future.successful(InternalServerError(Json.toJson(errorResponse)))
    } else if (nino.equalsIgnoreCase(invalidNinoETMPValidationError)) {
      val errors = errorCodeMap.toSeq
      val (code, text) = errors(scala.util.Random.nextInt(errors.length))

      val errorResponse = Json.obj(
        "errors" -> Json.arr(
          Json.obj(
            "processingDate" -> java.time.Instant.now().toString,
            "code" -> code,
            "text" -> text
          )
        )
      )

      Future.successful(UnprocessableEntity(errorResponse))
    } else {
      val successResponse = Json.obj(
        "success" -> Json.obj(
          "processingDate" -> java.time.Instant.now().toString,
          "taxPayerDisplayResponse" -> Json.obj(
            "safeId" -> "ZX1135522140666",
            "nino" -> nino,
            "mtdId" -> validMtditid,
            "yearOfMigration" -> "2025",
            "propertyIncomeFlag" -> true,
            "businessData" -> Json.arr(),
            "propertyData" -> Json.arr()
          )
        )
      )
      Future.successful(Ok(successResponse))
    }
  }
}
