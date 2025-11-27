
# self-assessment-liability-stub

This is the stub repository for [Self Assessment Liability Api](https://github.com/hmrc/self-assessment-liability-api)

## Stubbed Endpoints
There are 3 APIs stubbed in this repository. <br>
We use the following endpoints from [Citizen Details](https://github.com/hmrc/citizen-details) and [MTD Lookup Service](https://github.com/hmrc/mtd-identifier-lookup) to fetch the NINO and MTDITID associated with a UTR in order to authenticate a taxpayer with an MTD enrolment:

Method: `GET`

URL: `/citizen-details/sautr/:utr`

| Status Code | Description                                       |
|-------------|---------------------------------------------------|
| 200         | A record for the given SaUtr is found.            |
| 400         | Invalid `sautr` inputted.                         |
| 404         | No record for the given Nino or SaUtr is found.   |
| 500         | CID returned more than one valid matching result. |

Method: `GET`

URL: `/mtd-identifier-lookup/nino/:nino `

| Status Code | Description                             |
|-------------|-----------------------------------------|
| 200         | An MTDITID for the given Nino is found. |
| 400         | Invalid `NINO` inputted.                |
| 403         | Not authorised to perform operation.    |
| 500         | Downstream error.                       |


After a successful authorisation, a complex API in [HIP](https://admin.tax.service.gov.uk/integration-hub/apis/details/90582986-337d-4f9d-9aee-d367e9069e9d), is called which aggregates all self assessment data from 8 different APIs (3 from ETMP, 4 from CESA and one from [Payments API](https://github.com/hmrc/pay-api)) into one single endpoint. A random generator provides a response payload when this endpoint is called:

Method: `GET`

URL: `/as/self-assessment/account/:utr/liability-details`

Query Parameter: `dateFrom`

Query Parameter: `dateTo`

## Testing
Use the following command to run unit tests

```command
sbt test
```

## Running SA Liability API Stubs Locally

To run the stubs locally using SM2:

1. **Start all required services:**
```command
   sm2 --start SELF_ASSESSMENT_LIABILITY_API_ALL
```
2. **Stop the SA Liability API stubs service:**
```command
   sm2 --stop SELF_ASSESSMENT_LIABILITY_API_STUBS
```

3. **Run the stubs locally:**
```command
   sbt run
```

## Test Data and API Responses

API: `CID`

URL: `/citizen-details/sautr/:utr`

| UTR        | Description                    | Expected Behavior                                     |
|------------|--------------------------------|-------------------------------------------------------|
| 1100000404 | No NINO found for UTR          | 404 Not Found                                         |
| 1100000500 | Multiple NINOs (CID error)     | 500 Internal Server Error                             |
| 2200000400 | Invalid NINO returned          | 200 OK with invalid Nino `"NI0000400"` returned         |
| 2200000500 | NINO triggers downstream error | 200 OK with invalid Nino `"NI0000500"` returned         |
| Any other  | Good UTR                       | Success (200 OK) with valid Nino `"GG000000X"` returned |


API: `MTD Identifier Lookup`

URL: `/mtd-identifier-lookup/nino/:nino `

| NINO       | Description                    | Expected Behavior                                                |
|------------|--------------------------------|------------------------------------------------------------------|
| NI0000400  | Invalid NINO                   | 400 Bad Request                                                  |
| NI0000500  | NINO triggers server error     | 500 Internal Server Error                                        |
| Any other  | Valid NINO                     | 200 OK with valid MTDID key return return as `"XQIT00000000001"` |


API: `HIP`

URL: `/as/self-assessment/account/:utr/liability-details`

| UTR        | Description                                                | Expected Behavior                         |
|------------|------------------------------------------------------------|-------------------------------------------|
| 3300000400 | Invalid Correlation ID                                     | 400 Bad Request                           |
| 3300000401 | Invalid basic authentication credentials.                  | 401 Unauthorized                          |
| 3300000403 | User does not have authority to retrieve requested record. | 403 Forbidden                             |
| 3300000404 | UTR Not Found                                              | 404 Not Found                             |
| 3300000422 | Invalid UTR                                                | 422 Unprocessable Entity                  |
| 3300000500 | HIP Server Error                                           | 500 Internal Server Error                 |
| 3300000502 | External Service Error                                     | 502 Bad Gateway                           |
| 3300000503 | Service Unavailable                                        | 503 Service Unavailable                   |
| 3300000504 | Service Success with incorrect balances                    | 200 OK HIP response with invalid balances |
| 3300000505 | Service Success with correct balances                      | 200 OK HIP response with valid balances   |
| Any other  | Default HIP Response                                       | 200 OK HIP response generated             |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").