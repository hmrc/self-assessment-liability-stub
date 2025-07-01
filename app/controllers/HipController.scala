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

import utils.constants.RequestResponseConstants.*

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class HipController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  def getSelfAssessmentData(utr: String, dateFrom: String): Action[AnyContent] = Action.async {
    implicit request =>
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
        if (dateFrom.equals("2025-04-01")) {
          Future.successful(Ok(Json.toJson(validHipJsonResponse2025)))
        } else if (dateFrom.equals("2024-04-01")) {
          Future.successful(Ok(Json.toJson(validHipJsonResponse2024)))
        } else {
          Future.successful(Ok(Json.toJson(validHipJsonResponse2023)))
        }
      }
  }
}
