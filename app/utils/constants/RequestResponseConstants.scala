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

package utils.constants

object RequestResponseConstants {
  final val noNinoFoundForUtr: String = "1100000404"
  final val badUtrMultiple: String = "1100000500"
  final val badUtrInvalidNino: String = "2200000400"
  final val badUtrNinoServiceUnavailable: String = "2200000503"
  final val badUtrNinoETMPValidationError: String = "2200000422"
  final val badUtrHipInvalidCorrelationId: String = "3300000400"
  final val badUtrHipUnauthorised: String = "3300000401"
  final val badUtrHipForbidden: String = "3300000403"
  final val badUtrHipUtrNotFound: String = "3300000404"
  final val badUtrHipUtrInvalid: String = "3300000422"
  final val badUtrHipServerError: String = "3300000500"
  final val badUtrHipExternalServiceError: String = "3300000502"
  final val badUtrHipServiceUnavailable: String = "3300000503"
  final val badUtrHipInternalServiceError: String = "3300000504"
  final val goodUtrHipInternalService: String = "3300000505"
  final val utrWithOnlyBalanceDetails: String = "3333333333"
  final val utrErrorList: List[String] = List(
    badUtrHipInvalidCorrelationId,
    badUtrHipExternalServiceError,
    badUtrHipServerError,
    noNinoFoundForUtr,
    badUtrHipUtrNotFound,
    badUtrHipForbidden,
    badUtrHipUtrInvalid,
    badUtrMultiple,
    badUtrInvalidNino,
    badUtrHipUnauthorised,
    badUtrHipServiceUnavailable,
    badUtrHipInternalServiceError,
    goodUtrHipInternalService
  )
  final val validNino1: String = "GG000000X"
  final val validNino2: String = "GG000000Z"
  final val invalidNinoBadRequest: String = "NI0000400"
  final val invalidNinoServiceUnavailable: String = "NI0000503"
  final val invalidNinoETMPValidationError: String = "NI0000422"

  final val validMtditid: String = "XQIT00000000001"
}
