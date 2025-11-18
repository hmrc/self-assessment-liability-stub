
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

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").