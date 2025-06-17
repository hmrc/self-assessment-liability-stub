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
  final val badUtrInvalid: String = "0000000400"
  final val badUtrNone: String = "0000000404"
  final val badUtrMultiple: String = "0000000500"
  final val badUtrServerError: String = "1000000500"
  final val badUtrNinoInvalid: String = "0666666200"
  final val badUtrNinoServerError: String = "0777777200"
  final val badUtrHipServerError: String = "0111111500"

  final val validNino: String = "AA055075C"
  final val badNinoInvalid: String = "ss666666b"
  final val badNinoServerError: String = "ss777777b"

  final val validMtditid: String = "XQIT00000000001"

  final val validHipJsonResponse: String = """{
  "balanceDetails": {
    "totalOverdueBalance": 500.00,
    "totalPayableBalance": 500.00,
    "payableDueDate": "2025-04-31",
    "totalPendingBalance": 1500.00,
    "pendingDueDate": "2025-07-15",
    "totalBalance": 2000.00,
    "totalCodedOut": 250.00,
    "totalCreditAvailable": 0.00
  },
  "charges": [
    {
      "chargeId": "AB1234567",
      "creationDate": "2025-01-15",
      "chargeType": "ITSA",
      "chargeAmount": 1250.00,
      "outstandingAmount": 500.00,
      "taxYear": "2024-2025",
      "dueDate": "2025-04-31",
      "amendments": [
        {
          "amendmentId": "CD7654321",
          "amendmentType": "payment",
          "amendmentDate": "2025-04-15",
          "amendmentAmount": 500.00,
          "newChargeBalance": 750.00,
          "paymentReference": "PAY123456",
          "paymentMethod": "bank_transfer",
          "paymentDate": "2025-04-10"
        }
      ],
      "codedOutDetail": [
        {
          "amount": 250.00,
          "codedChargeType": "ITSA",
          "effectiveDate": "2025-04-01",
          "taxYear": "2024-2025",
          "effectiveTaxYear": "2025-2026"
        }
      ]
    },
    {
      "chargeId": "EF2345678",
      "creationDate": "2024-02-10",
      "chargeType": "NICS",
      "chargeAmount": 2200.00,
      "outstandingAmount": 0.00,
      "taxYear": "2023-2024",
      "dueDate": "2024-04-01",
      "interestStartDate": "2024-05-01",
      "interestEndDate": "2024-12-01",
      "interestRate": 0.05,
      "amendments": [
        {
          "amendmentId": "GH8765432",
          "amendmentType": "credit",
          "amendmentDate": "2024-04-31",
          "amendmentAmount": 200.00,
          "newChargeBalance": 2000.00,
          "paymentReference": "PAY888233",
        },
        {
          "amendmentId": "CD7654321",
          "amendmentType": "payment",
          "amendmentDate": "2024-12-08",
          "amendmentAmount": 2058.33,
          "newChargeBalance": 0.00,
          "paymentReference": "PAY112233",
          "paymentMethod": "bank_transfer",
          "paymentDate": "2024-12-03"
        }
      ]
    },
    {
      "chargeId": "KL3456789",
      "creationDate": "2025-05-22",
      "chargeType": "VATC",
      "chargeAmount": 1500.00,
      "outstandingAmount": 1500.00,
      "taxYear": "2025-2026",
      "dueDate": "2025-07-15"
    }
  ],
  "refunds": [
    {
      "issueDate": "2024-01-10",
      "refundMethod": "bank_transfer",
      "refundRequestDate": "2023-12-12",
      "refundRequestAmount": 350,
      "refundReference": "REF123456",
      "interestAddedToRefund": 5.25,
      "refundActualAmount": 355.25,
      "refundStatus": "processed",
    }
  ],
    "paymentHistory": [
    {
      "paymentAmount": 500.00 ,
      "paymentReference": "PAY123456",
      "paymentMethod": "bank_transfer",
      "paymentDate": "2025-04-11"
      "dateProcessed": "2025-04-15",
      "allocationReference": "AB1234567",
    },
    {
      "paymentAmount": 2058.33 ,
      "paymentReference": "PAY112233",
      "paymentMethod": "bank_transfer",
      "paymentDate": "2024-12-04"
      "dateProcessed": "2024-12-08",
      "allocationReference": "EF2345678",
    },
        {
      "paymentAmount": 200.00 ,
      "paymentReference": "PAY888233",
      "paymentMethod": "bank_transfer",
      "paymentDate": "2023-12-04"
      "dateProcessed": "2023-12-08",
      "allocationReference": "EF2345678",
    }
  ]
}"""
}
