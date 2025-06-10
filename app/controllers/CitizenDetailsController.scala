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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class CitizenDetailsController @Inject() (cc: ControllerComponents)
    extends BackendController(cc)
    with StubData {

  def generateSuccessResponse(nino: String): String = {
    s"""
    {
      "name": {
        "current": {
          "firstName": "John",
          "lastName": "Smith"
        },
        "previous": []
      },
      "ids": {
        "nino": "$nino"
      },
      "dateOfBirth": "11121971"
    }
    """
  }

  def getNino(utr: String): Action[AnyContent] = Action.async { implicit request =>
    if (utr.equalsIgnoreCase(badUtrInvalid)) {
      Future.successful(
        BadRequest(Json.obj("message" -> "Invalid SaUtr."))
      )
    } else if (utr.equalsIgnoreCase(badUtrNone)) {
      Future.successful(
        NotFound(Json.obj("message" -> "No record for the given SaUtr is found."))
      )
    } else if (utr.equalsIgnoreCase(badUtrMultiple)) {
      Future.successful(
        InternalServerError(Json.obj("message" -> "More than one valid matching result."))
      )
    } else if (utr.equalsIgnoreCase(badUtrServerError)) {
      Future.successful(
        InternalServerError(Json.obj("message" -> "Service currently unavailable"))
      )
    } else if (utr.equalsIgnoreCase(badUtrNinoInvalid)) {
      Future.successful(
        Ok(Json.parse(generateSuccessResponse(badNinoInvalid)))
      )
    } else if (utr.equalsIgnoreCase(badUtrNinoServerError)) {
      Future.successful(
        Ok(Json.parse(generateSuccessResponse(badNinoServerError)))
      )
    } else {
      Future.successful(
        Ok(Json.parse(generateSuccessResponse(validNino)))
      )
    }
  }
}
