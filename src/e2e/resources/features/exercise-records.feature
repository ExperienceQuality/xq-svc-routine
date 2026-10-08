@records @e2e
Feature: Compose exercise records for a routine session
  Record composition reads exercise data but never transfers ownership to the routine service.

  Background:
    Given I created a routine named "Record composition routine"
    And I added session "Strength" at position 0 with exercises "Squat, Bench Press"

  @smoke
  Scenario: Compose records in session exercise order
    Given the exercise service returns these logs for "Squat":
      | id | exerciseName | loggedAt                  | highestSetVolumeKg |
      | 11 | Squat        | 2026-09-01T08:00:00.000Z | 500                |
    And the exercise service returns these logs for "Bench Press":
      | id | exerciseName | loggedAt                  | highestSetVolumeKg |
      | 22 | Bench Press  | 2026-09-02T08:00:00.000Z | 300                |
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 200
    And the composed records have exercise names in order "Squat, Bench Press"
    And the exercise service received one GET for "Squat"
    And the exercise service received one GET for "Bench Press"
    And the exercise service received no mutation request

  Scenario: Preserve complete exercise-owned record data
    Given the exercise service returns a detailed log for "Squat"
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 200
    And the composed response contains the complete detailed log for "Squat"

  Scenario: Return an empty collection for an exercise with no history
    Given the exercise service returns no logs for "Squat"
    And the exercise service returns no logs for "Bench Press"
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 200
    And the composed record collection is empty

  Scenario: Match exercise names case-insensitively
    Given the exercise service returns these logs for "Squat":
      | id | exerciseName | loggedAt                  | highestSetVolumeKg |
      | 31 | SQUAT        | 2026-09-03T08:00:00.000Z | 600                |
    And the exercise service returns no logs for "Bench Press"
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 200
    And the composed records have exercise names in order "SQUAT"

  Scenario: Reject record composition without a session ID
    When I retrieve exercise records without a session ID
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"

  Scenario: Return not found for an unknown session during composition
    When I retrieve exercise records for an unknown session
    Then the response status is 404
    And the problem code is "SESSION_NOT_FOUND"

  Scenario: Map exercise-service unavailability to service unavailable
    Given the exercise service is unavailable for "Squat"
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 503
    And the problem code is "EXERCISE_SERVICE_UNAVAILABLE"

  Scenario: Map exercise-service rate limiting to service unavailable
    Given the exercise service rate limits "Squat" for 30 seconds
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 503
    And the problem code is "EXERCISE_SERVICE_UNAVAILABLE"
    And the response Retry-After header is "30"

  Scenario: Map malformed exercise data to bad gateway
    Given the exercise service returns malformed data for "Squat"
    When I retrieve exercise records for the current session with limit 20
    Then the response status is 502
    And the problem code is "DOWNSTREAM_BAD_RESPONSE"

  Scenario: Routine definition remains readable when exercise service is down
    Given the exercise service is unavailable for every request
    When I retrieve the current routine
    Then the response status is 200
    And session "Strength" has exercises in order:
      | exerciseName |
      | Squat        |
      | Bench Press  |
    And the exercise service was not called

  Scenario: Continue composed records with an opaque cursor
    Given the exercise service returns these logs for "Squat":
      | id | exerciseName | loggedAt                  | highestSetVolumeKg |
      | 51 | Squat        | 2026-09-06T08:00:00.000Z | 500                |
      | 52 | Squat        | 2026-09-05T08:00:00.000Z | 450                |
    And the exercise service returns these logs for "Bench Press":
      | id | exerciseName | loggedAt                  | highestSetVolumeKg |
      | 53 | Bench Press  | 2026-09-04T08:00:00.000Z | 300                |
    When I retrieve exercise records for the current session with limit 2
    Then the composed record list has 2 items
    And the composed record list has a next cursor
    When I follow the composed record next cursor with limit 2
    Then the response status is 200
    And the two composed record pages have no duplicate IDs

  Scenario: Reject an invalid composed-record cursor
    When I retrieve exercise records with invalid cursor "not-a-valid-cursor"
    Then the response status is 400
    And the problem code is "INVALID_CURSOR"

  Scenario Outline: Validate record page size
    When I retrieve exercise records for the current session with limit <limit>
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"

    Examples:
      | limit |
      | 0     |
      | 101   |
