@routine @e2e
Feature: Manage routine definitions
  A client can maintain routine metadata without contacting the exercise service.

  @smoke
  Scenario: Create and retrieve a routine
    When I create a routine named "Push day" with notes "Chest and shoulders"
    Then the response status is 201
    And the response has a Location header for the routine
    And the response has a strong ETag
    And the routine response has name "Push day" and notes "Chest and shoulders"
    And the routine response has no sessions
    When I retrieve the current routine
    Then the response status is 200
    And the routine response has name "Push day" and notes "Chest and shoulders"
    And the response ETag equals the saved ETag
    And the exercise service was not called

  Scenario: Create a routine without notes
    When I create a routine named "Recovery" without notes
    Then the response status is 201
    And the routine response has name "Recovery" and null notes

  Scenario: Trim routine text on creation
    When I create a routine named "  Pull day  " with notes "  Back work  "
    Then the response status is 201
    And the routine response has name "Pull day" and notes "Back work"

  Scenario Outline: Reject an invalid routine creation
    When I submit a routine creation with name <name>
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"
    And the problem identifies field "name"

    Examples:
      | name                                                                                                                           |
      | ""                                                                                                                             |
      | "   "                                                                                                                          |
      | "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" |

  Scenario: Reject unknown properties on creation
    When I submit a routine creation containing an unknown property
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"

  Scenario: Reject malformed JSON on creation
    When I submit malformed JSON to create a routine
    Then the response status is 400
    And the problem code is "MALFORMED_REQUEST"

  Scenario: List active routines
    Given I created a routine named "Routine visible in list"
    When I list active routines with limit 20
    Then the response status is 200
    And the routine list contains the current routine
    And the routine list does not expose sessions

  Scenario: Update routine metadata using the current ETag
    Given I created a routine named "Old name"
    When I update the routine name to "New name" and notes to "New notes" with the saved ETag
    Then the response status is 200
    And the routine response has name "New name" and notes "New notes"
    And the response has a newer strong ETag

  Scenario: Clear routine notes using merge patch
    Given I created a routine named "Routine with notes" and notes "Remove me"
    When I clear the routine notes with the saved ETag
    Then the response status is 200
    And the routine response has name "Routine with notes" and null notes

  Scenario: Require If-Match when updating a routine
    Given I created a routine named "Protected routine"
    When I update the routine without If-Match
    Then the response status is 428
    And the problem code is "PRECONDITION_REQUIRED"

  Scenario: Reject a stale ETag when updating a routine
    Given I created a routine named "Concurrently edited routine"
    And I saved the current ETag as stale
    And I update the routine name to "First update" with the saved ETag
    When I update the routine name to "Stale update" with the stale ETag
    Then the response status is 412
    And the problem code is "PRECONDITION_FAILED"

  Scenario: Archive a routine using the current ETag
    Given I created a routine named "Routine to archive"
    When I archive the current routine with the saved ETag
    Then the response status is 204
    When I retrieve the current routine
    Then the response status is 200
    And the routine response is archived
    When I list active routines with limit 20
    Then the routine list does not contain the current routine
    When I list routines including archived routines with limit 20
    Then the routine list contains the current routine

  Scenario: Require If-Match when archiving a routine
    Given I created a routine named "Archive protected routine"
    When I archive the current routine without If-Match
    Then the response status is 428
    And the problem code is "PRECONDITION_REQUIRED"

  Scenario: Return not found for an unknown routine
    When I retrieve an unknown routine
    Then the response status is 404
    And the problem code is "ROUTINE_NOT_FOUND"

  Scenario: Routine CRUD never calls the exercise service
    Given the exercise service is unavailable for every request
    When I create a routine named "Independent routine" without notes
    Then the response status is 201
    When I retrieve the current routine
    Then the response status is 200
    When I update the routine name to "Still independent" with the saved ETag
    Then the response status is 200
    And the exercise service was not called
