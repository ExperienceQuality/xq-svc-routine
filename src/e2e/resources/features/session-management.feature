@session @e2e
Feature: Manage ordered sessions in a routine
  Sessions are routine-owned definitions containing ordered exercise-name references.

  Background:
    Given I created a routine named "Session test routine"

  @smoke
  Scenario: Add a session with ordered exercise references
    When I add session "Upper body" at position 0 with exercises:
      | exerciseName  |
      | Bench Press   |
      | Shoulder Press |
      | Triceps Pushdown |
    Then the response status is 201
    And the response has a newer strong ETag
    And session "Upper body" is at position 0
    And session "Upper body" has exercises in order:
      | exerciseName     |
      | Bench Press      |
      | Shoulder Press   |
      | Triceps Pushdown |
    And the exercise service was not called

  Scenario: Add multiple sessions and preserve their order
    Given I added session "Lower body" at position 0 with exercises "Squat, Deadlift"
    When I add session "Upper body" at position 0 with exercises:
      | exerciseName |
      | Bench Press  |
    Then the response status is 201
    And the routine has sessions in order "Upper body, Lower body"

  Scenario: Replace a session atomically
    Given I added session "Old session" at position 0 with exercises "Squat, Lunge"
    When I replace the current session with name "New session" at position 0 and exercises:
      | exerciseName |
      | Deadlift     |
      | Hip Thrust   |
    Then the response status is 200
    And session "New session" has exercises in order:
      | exerciseName |
      | Deadlift     |
      | Hip Thrust   |
    And the routine contains no session named "Old session"

  Scenario: Move a session and compact positions
    Given I added session "First" at position 0 with exercises "Squat"
    And I added session "Second" at position 1 with exercises "Bench Press"
    When I replace session "Second" with name "Second" at position 0 and exercises "Bench Press"
    Then the response status is 200
    And the routine has sessions in order "Second, First"
    And session positions are contiguous from zero

  Scenario: Delete a session and compact positions
    Given I added session "First" at position 0 with exercises "Squat"
    And I added session "Second" at position 1 with exercises "Bench Press"
    When I delete session "First" with the saved ETag
    Then the response status is 204
    When I retrieve the current routine
    Then the routine has sessions in order "Second"
    And session positions are contiguous from zero

  Scenario: Require If-Match when adding a session
    When I add a session without If-Match
    Then the response status is 428
    And the problem code is "PRECONDITION_REQUIRED"

  Scenario: Reject stale ETag when adding a session
    Given I saved the current ETag as stale
    And I added session "Accepted" at position 0 with exercises "Squat"
    When I add session "Rejected" with the stale ETag
    Then the response status is 412
    And the problem code is "PRECONDITION_FAILED"

  Scenario: Require If-Match when replacing a session
    Given I added session "Replace protected" at position 0 with exercises "Squat"
    When I replace the current session without If-Match
    Then the response status is 428
    And the problem code is "PRECONDITION_REQUIRED"

  Scenario: Reject stale ETag when replacing a session
    Given I added session "Stale replace" at position 0 with exercises "Squat"
    And I saved the current ETag as stale
    And I update the routine name to "Version advanced" with the saved ETag
    When I replace session "Stale replace" with the stale ETag
    Then the response status is 412
    And the problem code is "PRECONDITION_FAILED"

  Scenario: Require If-Match when deleting a session
    Given I added session "Delete protected" at position 0 with exercises "Squat"
    When I delete the current session without If-Match
    Then the response status is 428
    And the problem code is "PRECONDITION_REQUIRED"

  Scenario: Reject stale ETag when deleting a session
    Given I added session "Stale delete" at position 0 with exercises "Squat"
    And I saved the current ETag as stale
    And I update the routine name to "Version advanced" with the saved ETag
    When I delete session "Stale delete" with the stale ETag
    Then the response status is 412
    And the problem code is "PRECONDITION_FAILED"

  Scenario Outline: Reject an invalid session name
    When I add a session named <name>
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"
    And the problem identifies field "name"

    Examples:
      | name                                                                                                                           |
      | ""                                                                                                                             |
      | "   "                                                                                                                          |
      | "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" |

  Scenario: Reject duplicate exercise names ignoring case and whitespace
    When I add session "Duplicates" at position 0 with exercises:
      | exerciseName   |
      | Bench Press    |
      |  bench press   |
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"
    And the problem identifies field "exercises"

  Scenario: Reject a blank exercise name
    When I add session "Invalid exercise" at position 0 with exercises:
      | exerciseName |
      | Squat        |
      |              |
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"
    And the problem identifies field "exercises[1].name"

  Scenario: Reject more than 50 exercise references
    When I add a session containing 51 exercise names
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"

  Scenario: Reject a 51st session
    Given the routine already has 50 sessions
    When I add session "Too many" at position 50 with exercises:
      | exerciseName |
      | Squat        |
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"

  Scenario: Return not found for an unknown session
    When I replace an unknown session with the saved ETag
    Then the response status is 404
    And the problem code is "SESSION_NOT_FOUND"

  Scenario: Return not found when adding a session to an unknown routine
    When I add a session to an unknown routine
    Then the response status is 404
    And the problem code is "ROUTINE_NOT_FOUND"

  Scenario: Reject session mutation on an archived routine
    Given I archived the current routine
    When I add session "Late session" with the saved ETag
    Then the response status is 409
    And the problem code is "ROUTINE_ARCHIVED"

  Scenario: Session writes never mutate the exercise service
    Given the exercise service is unavailable for every request
    When I add session "Local definition" at position 0 with exercises:
      | exerciseName |
      | Unknown Move |
    Then the response status is 201
    And the exercise service was not called
