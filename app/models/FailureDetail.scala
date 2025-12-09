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

package models

import play.api.libs.json.{Format, Json}

case class HipErrorResponse(origin: String, response: ResponseWrapper)
case class ResponseWrapper(failures: List[FailureDetail])
case class FailureDetail(`type`: String, reason: String)

object HipErrorResponse {
  implicit val errorResponseFormat: Format[HipErrorResponse] = Json.format
}
object ResponseWrapper {
  implicit val responseWrapperFormat: Format[ResponseWrapper] = Json.format
}
object FailureDetail {
  implicit val failureDetailFormat: Format[FailureDetail] = Json.format
}
