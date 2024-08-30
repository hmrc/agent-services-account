
# agent-services-account

## What the service does

This is a backend microservice to store all pertinent data for Agent Services Accounts.

## Running the tests

    sbt test it/test

## Running the app locally

    sm2 --start AGENT_ONBOARDING
    sm2 --stop AGENT_SUBSCRIPTION
    sbt run

## Endpoints

### GET /agent-services-account/change-of-details-request/:arn

This endpoint is used to retrieve the change of details request for the given ARN.

#### Expected responses

 - 200 OK with JSON body

Example body
```json
{
  "arn": "AARN1234567",
  "timeSubmitted": "2024-08-21T12:34:56.123456Z"
}
```

 - 404 Not Found

### POST /agent-services-account/change-of-details-request/

This endpoint is used to create/update a change of details request. It requires a JSON body with the ARN of the agent and an Instant of the time the request was submitted.

#### Example Payload

```json
{
  "arn": "AARN1234567",
  "timeSubmitted": "2024-08-21T12:34:56.123456Z"
}
```

#### Expected response

 - 204 NoContent

### DELETE /agent-services-account/change-of-details-request/:arn
This endpoint is used to delete the change of details request for the given ARN.

#### Expected responses

 - 204 NoContent
 - 404 Not Found

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
