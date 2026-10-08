@health @e2e
Feature: Report service health

  @smoke
  Scenario: Liveness does not depend on PostgreSQL or the exercise service
    When I request service liveness
    Then the response status is 200
    And the health status is "UP"

  Scenario: Readiness reports a usable service
    When I request service readiness
    Then the response status is 200
    And the health status is "UP"
