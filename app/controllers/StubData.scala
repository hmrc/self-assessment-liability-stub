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

trait StubData {
  final val badUtrInvalid:         String = "0000000400"
  final val badUtrNone:            String = "0000000404"
  final val badUtrMultiple:        String = "0000000500"
  final val badUtrServerError:     String = "1000000500"
  final val badUtrNinoInvalid:     String = "0666666200"
  final val badUtrNinoServerError: String = "0777777200"

  final val validNino:          String = "AA055075C"
  final val badNinoInvalid:     String = "ss666666b"
  final val badNinoServerError: String = "ss777777b"

  final val validMtditid: String = "XQIT00000000001"
}
