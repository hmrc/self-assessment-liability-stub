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

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.constants.RequestResponseConstants.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class CitizenDetailsController @Inject() (cc: ControllerComponents) extends BackendController(cc) with Logging{

  def generateSuccessResponse(ninos: List[String]): String = {
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
        "nino": "${ninos.mkString(",")}"
      },
      "dateOfBirth": "11121971"
    }
    """
  }

  def getNino(utr: String): Action[AnyContent] = Action.async { implicit request =>
     if (utr.equalsIgnoreCase(noNinoFoundForUtr)) {
      Future.successful(
        NotFound(Json.obj("message" -> "No record for the given SaUtr is found."))
      )
    } else if (utr.equalsIgnoreCase(badUtrMultiple)) {
       val validNinos= List(validNino,validNino)
       logger.info(s"${validNinos.length} valid ninos associated with $badUtrMultiple returned from CID")
      Future.successful(
        InternalServerError(Json.parse(generateSuccessResponse(List(validNino,validNino))))
      )
    } else if (utr.equalsIgnoreCase(badUtrInvalidNino)) {
      Future.successful(
        Ok(Json.parse(generateSuccessResponse(List(invalidNino))))
      )
    } else if (utr.equalsIgnoreCase(badUtrNinoServerError)) {
      Future.successful(
        InternalServerError(Json.obj("message" -> "Downstream Error"))
      )
    } else {
      Future.successful(
        Ok(Json.parse(generateSuccessResponse(List(validNino))))
      )
    }
  }
}
