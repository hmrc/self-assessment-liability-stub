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

import models.HipResponse
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.HipService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.constants.RequestResponseConstants.*

import java.time.format.DateTimeParseException
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class HipController @Inject() (cc: ControllerComponents, service: HipService)
    extends BackendController(cc)
    with Logging {
  def getSelfAssessmentData(utr: String, dateFrom: String, dateTo: String): Action[AnyContent] = {
    val message: String =
      if utrErrorList.contains(utr) then
        s"Calling HIP with $utr which is a test data that will result in an error"
      else s"Calling HIP with $utr"
    logger.info(message)
    Action.async { implicit request =>
      if (utr.equalsIgnoreCase(badUtrHipInvalidCorrelationId)) {
        Future.successful(
          BadRequest(
            Json.obj("message" -> "Submission has not passed validation. Invalid Correlation Id.")
          )
        )
      } else if (utr.equalsIgnoreCase(badUtrHipUnauthorised)) {
        Future.successful(
          Unauthorized(Json.obj("message" -> "Invalid basic authentication credentials."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipForbidden)) {
        Future.successful(
          Forbidden(
            Json.obj("message" -> "User does not have authority to retrieve requested record.")
          )
        )
      } else if (utr.equalsIgnoreCase(badUtrHipUtrNotFound)) {
        Future.successful(
          NotFound(Json.obj("message" -> "Identifier not found."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipUtrInvalid)) {
        Future.successful(
          UnprocessableEntity(Json.obj("message" -> "Invalid utr entered."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipServerError)) {
        Future.successful(
          InternalServerError(Json.obj("message" -> "Internal server error."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipExternalServiceError)) {
        Future.successful(
          BadGateway(Json.obj("message" -> "Error communicating with external service."))
        )
      } else if (utr.equalsIgnoreCase(badUtrHipServiceUnavailable)) {
        Future.successful(
          ServiceUnavailable(Json.obj("message" -> "Service unavailable"))
        )
      } else {
        try {
          val hipResponse: HipResponse = service.generateHipResponse(dateFrom, dateTo)
          val json: JsValue = Json.toJson(hipResponse)
          Future.successful(Ok(json))
        } catch {
          case _: DateTimeParseException =>
            Future.successful(
              BadRequest(
                Json.obj(
                  "message" -> "Invalid date inputted. The date needs to follow YYYY-MM-DD format"
                )
              )
            )
        }

      }
    }
  }
}
