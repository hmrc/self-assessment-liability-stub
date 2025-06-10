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

object CitizenDetailsController {
  final val badUtrInvalid:         String = "0000000400"
  final val badUtrNone:            String = "0000000404"
  final val badUtrMultiple:        String = "0000000500"
  final val badUtrServerError:     String = "1000000500"
  final val badUtrNinoInvalid:     String = "0666666200"
  final val badUtrNinoServerError: String = "0777777200"

  final val validNino: String = "AA055075C"
}

@Singleton()
class CitizenDetailsController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  def generateResponse(nino: String): String = {
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
    if (utr.equalsIgnoreCase(CitizenDetailsController.badUtrInvalid)) {
      Future.successful(
        BadRequest(Json.obj("message" -> "Invalid SaUtr."))
      )
    } else if (utr.equalsIgnoreCase(CitizenDetailsController.badUtrNone)) {
      Future.successful(
        NotFound(Json.obj("message" -> "No record for the given SaUtr is found."))
      )
    } else if (utr.equalsIgnoreCase(CitizenDetailsController.badUtrMultiple)) {
      Future.successful(
        InternalServerError(Json.obj("message" -> "More than one valid matching result."))
      )
    } else if (utr.equalsIgnoreCase(CitizenDetailsController.badUtrServerError)) {
      Future.successful(
        InternalServerError(Json.obj("message" -> "Service currently unavailable"))
      )
    } else if (utr.equalsIgnoreCase(CitizenDetailsController.badUtrNinoInvalid)) {
      Future.successful(
        Ok(Json.parse(generateResponse(MtdIdLookupController.badNinoInvalid)))
      )
    } else if (utr.equalsIgnoreCase(CitizenDetailsController.badUtrNinoServerError)) {
      Future.successful(
        Ok(Json.parse(generateResponse(MtdIdLookupController.badNinoServerError)))
      )
    } else {
      Future.successful(
        Ok(Json.parse(generateResponse(CitizenDetailsController.validNino)))
      )
    }
  }
}
