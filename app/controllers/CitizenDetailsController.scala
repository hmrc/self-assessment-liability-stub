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
class CitizenDetailsController @Inject() (cc: ControllerComponents)
    extends BackendController(cc)
    with Logging {

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
    val message: String =
      if utrErrorList.contains(utr) then
        s"Calling CID with $utr which is a test data that will result in an error"
      else s"Calling CID with $utr"
    logger.info(message)
    utr match {
      case u if u == "3384286946" =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List("WP105133A")))))
      case u if u == "1992665564" =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List("WP105333A")))))
      case u if u == "4809635190" =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List("WP105533A")))))
      case u if u == "2112635977" =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List("WP184333A")))))
      case u if u == "3601373390" =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List("WP120333A")))))
      case u if u == "1405365362" =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List("WP071433A")))))
      case u if u == noNinoFoundForUtr =>
        Future.successful(
          NotFound(Json.obj("message" -> "No record for the given SaUtr is found."))
        )
      case u if u == badUtrMultiple =>
        val validNinos = List(validNino1, validNino2)
        Future.successful(InternalServerError(Json.parse(generateSuccessResponse(validNinos))))
      case u if u == badUtrInvalidNino =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List(invalidNinoBadRequest)))))
      case u if u == badUtrNinoServerError =>
        Future.successful(Ok(Json.parse(generateSuccessResponse(List(invalidNinoServerError)))))
      case u if u == badUtrNinoServiceUnavailable =>
        Future.successful(
          Ok(Json.parse(generateSuccessResponse(List(invalidNinoServiceUnavailable))))
        )
      case u if u == badUtrNinoETMPValidationError =>
        Future.successful(
          Ok(Json.parse(generateSuccessResponse(List(invalidNinoETMPValidationError))))
        )
      case _ => Future.successful(Ok(Json.parse(generateSuccessResponse(List(validNino1)))))
    }
  }
}
