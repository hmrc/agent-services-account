
# agent-services-account

## What the service does

This is a backend microservice for the Agent Services account page. It is available to agents who have the HMRC-AS-AGENT enrolment,
allowing them to access to a range of HMRC digital services.

## Running the tests

    sbt test it:test

## Running the app locally

    sm2 --start AGENT_ONBOARDING
    sm2 --stop AGENT_SUBSCRIPTION
    sbt run

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
