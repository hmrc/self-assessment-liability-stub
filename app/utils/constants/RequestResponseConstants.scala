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
  final val noNinoFoundForUtr: String = "CD00000404"
  final val badUtrMultiple: String = "CD00000500"
  final val badUtrInvalidNino: String = "MT00000400"
  final val badUtrNinoServerError: String = "MT00000500"
  final val badUtrHipInvalidCorrelationId: String = "HIP0000400"
  final val badUtrHipUnauthorised: String = "HIP0000401"
  final val badUtrHipForbidden: String = "HIP0000403"
  final val badUtrHipUtrNotFound: String = "HIP0000404"
  final val badUtrHipUtrInvalid: String = "HIP0000422"
  final val badUtrHipServerError: String = "HIP0000500"
  final val badUtrHipExternalServiceError: String = "HIP0000502"
  final val badUtrHipServiceUnavailable: String = "HIP0000503"

  final val validNino1: String = "GG000000X"
  final val validNino2: String = "GG000000Z"
  final val invalidNino: String = "NI0000400"
  final val badNinoServerError: String = "NI0000500"

  final val validMtditid: String = "XQIT00000000001"
}
