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

import models.{
  BalanceDetails,
  HipError,
  HipErrorDetails,
  HipResponse,
  HipResponseError,
  PaymentHistoryDetails
}
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request, Result}
import services.HipService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.ResponseGenerator.today
import utils.constants.RequestResponseConstants.*

import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.Random
import java.util.UUID

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
      validateHeaders(request) match {
        case Some(errorResponse) =>
          Future.successful(errorResponse)
        case None =>
          utr match {
            case u if u == badUtrHipInvalidCorrelationId =>
              val error = createErrorResponse(
                "HIP",
                None,
                "INVALID_CORRELATIONID",
                "Submission has not passed validation. Invalid CorrelationId."
              )
              Future.successful(BadRequest(Json.toJson(error)))

            case u if u == badUtrHipUnauthorised =>
              val error = createErrorResponse(
                "HIP",
                None,
                "UNAUTHORIZED",
                "Invalid basic authentication credentials."
              )
              Future.successful(Unauthorized(Json.toJson(error)))

            case u if u == badUtrHipForbidden =>
              val error = createErrorResponse(
                "HoD",
                Some("ITSA Repayments Viewer"),
                "FORBIDDEN",
                "User does not have authority to retrieve requested record."
              )
              Future.successful(Forbidden(Json.toJson(error)))

            case u if u == badUtrHipUtrNotFound =>
              val error = createErrorResponse(
                "HoD",
                Some("ITSA Repayments Viewer"),
                "NOT_FOUND",
                "Identifier not found."
              )
              Future.successful(NotFound(Json.toJson(error)))

            case u if u == badUtrHipUtrInvalid =>
              val error = createErrorResponse(
                "HoD",
                Some("ITSA Repayments Viewer"),
                "48003",
                "Invalid UTR entered."
              )
              Future.successful(UnprocessableEntity(Json.toJson(error)))

            case u if u == badUtrHipServerError =>
              val error = createErrorResponse("HIP", None, "SERVER_ERROR", "Internal server error.")
              Future.successful(InternalServerError(Json.toJson(error)))

            case u if u == badUtrHipExternalServiceError =>
              val error = createErrorResponse(
                "HoD",
                Some("SA Balance and Transaction details"),
                "BAD_GATEWAY",
                "Error communicating with external service."
              )
              Future.successful(BadGateway(Json.toJson(error)))

            case u if u == badUtrHipServiceUnavailable =>
              val error =
                createErrorResponse("HIP", None, "SERVICE_UNAVAILABLE", "Service unavailable")
              Future.successful(ServiceUnavailable(Json.toJson(error)))

            case u if u == badUtrHipInternalServiceError =>
              val hipResponse: HipResponse = HipResponse(
                balanceDetails = BalanceDetails(
                  totalOverdueBalance = 0,
                  totalPayableBalance = 100,
                  earliestPayableDueDate = None,
                  totalPendingBalance = 100,
                  earliestPendingDueDate = None,
                  totalBalance = 200,
                  totalCreditAvailable = 0,
                  codedOutDetail = List.empty
                ),
                chargeDetails = List.empty,
                refundDetails = List.empty,
                paymentHistoryDetails = List.empty
              )
              val json = Json.toJson(hipResponse)
              Future.successful(Ok(json))
            case u if u == goodUtrHipInternalService =>
              val hipResponse: HipResponse = HipResponse(
                balanceDetails = BalanceDetails(
                  totalOverdueBalance = 0,
                  totalPayableBalance = 100,
                  earliestPayableDueDate = Some(today.plusDays(Random.nextInt(30))),
                  totalPendingBalance = 100,
                  earliestPendingDueDate = Some(today.plusDays(31 + Random.nextInt(150))),
                  totalBalance = 200,
                  totalCreditAvailable = 0,
                  codedOutDetail = List.empty
                ),
                chargeDetails = List.empty,
                refundDetails = List.empty,
                paymentHistoryDetails = List.empty
              )
              val json = Json.toJson(hipResponse)
              Future.successful(Ok(json))
            case u if u == utrWithOnlyBalanceDetails =>
              val minimalHipResponseJson: JsValue = Json.obj(
                "balanceDetails" -> Json.obj(
                  "totalOverdueBalance" -> 0,
                  "totalPayableBalance" -> 0,
                  "totalPendingBalance" -> 0,
                  "totalBalance" -> 0,
                  "totalCreditAvailable" -> 0
                )
              )
              Future.successful(Ok(minimalHipResponseJson))
            case u if u == badUtrWithoutPaymentReference =>
              val hipResponse: HipResponse = HipResponse(
                balanceDetails = BalanceDetails(
                  totalOverdueBalance = 0,
                  totalPayableBalance = 100,
                  earliestPayableDueDate = Some(today.plusDays(Random.nextInt(30))),
                  totalPendingBalance = 100,
                  earliestPendingDueDate = Some(today.plusDays(31 + Random.nextInt(150))),
                  totalBalance = 200,
                  totalCreditAvailable = 0,
                  codedOutDetail = List.empty
                ),
                chargeDetails = List.empty,
                refundDetails = List.empty,
                paymentHistoryDetails = List(
                  PaymentHistoryDetails(
                    paymentAmount = 500.00,
                    paymentReference = None,
                    paymentMethod = Some("bank transfer"),
                    paymentDate = LocalDate.of(2023, 2, 15),
                    processedDate = Some(LocalDate.of(2023, 2, 16)),
                    allocationReference = Some("charge-123")
                  )
                )
              )
              val json = Json.toJson(hipResponse)
              Future.successful(Ok(json))
            case _ =>
              try {
                val hipResponse: HipResponse = service.generateHipResponse(dateFrom, dateTo)
                val json: JsValue = Json.toJson(hipResponse)
                Future.successful(Ok(json))
              } catch {
                case _: DateTimeParseException =>
                  val error = createErrorResponse(
                    "HIP",
                    None,
                    "INVALID_DATE_FORMAT",
                    "Invalid date inputted. The date needs to follow YYYY-MM-DD format"
                  )
                  Future.successful(BadRequest(Json.toJson(error)))
              }
          }
      }
    }
  }

  private def validateHeaders(request: Request[AnyContent]): Option[Result] = {
    val authorization = request.headers.get("Authorization")
    val correlationId = request.headers.get("CorrelationId")
    val contentType = request.headers.get("Content-Type")

    if (correlationId.isEmpty || !isValidUUID(correlationId.getOrElse(""))) {
      val error = createErrorResponse(
        "HIP",
        None,
        "MISSING_HEADER",
        "Correlation-ID header is required and must be in UUID format"
      )
      Some(BadRequest(Json.toJson(error)))
    } else if (authorization.isEmpty) {
      val error = createErrorResponse(
        "HIP",
        None,
        "MISSING_HEADER",
        "Authorization header is required"
      )
      Some(BadRequest(Json.toJson(error)))
    } else if (contentType.isEmpty) {
      val error = createErrorResponse(
        "HIP",
        None,
        "MISSING_HEADER",
        "Content-Type header is required"
      )
      Some(BadRequest(Json.toJson(error)))
    } else {
      None
    }
  }

  private def isValidUUID(uuid: String): Boolean = {
    try {
      UUID.fromString(uuid)
      true
    } catch {
      case _: IllegalArgumentException => false
    }
  }

  private def createErrorResponse(
      origin: String,
      service: Option[String],
      errorType: String,
      reason: String
  ): HipResponseError = {
    HipResponseError(
      origin = origin,
      service = service,
      response = HipErrorDetails(
        failures = List(HipError(errorType, reason))
      )
    )
  }
}
