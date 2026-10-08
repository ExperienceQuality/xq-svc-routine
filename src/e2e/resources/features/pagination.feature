@pagination @e2e
Feature: Navigate routine collections
  Collection cursors are opaque, stable, and bound to their filters.

  Scenario: Continue a routine list with an opaque cursor
    Given I created 3 active routines for pagination
    When I list active routines with limit 2
    Then the response status is 200
    And the routine list has 2 items
    And the routine list has a next cursor
    When I follow the routine list next cursor with limit 2
    Then the response status is 200
    And the two routine pages have no duplicate IDs

  Scenario: Reject an invalid routine cursor
    When I list routines with invalid cursor "not-a-valid-cursor"
    Then the response status is 400
    And the problem code is "INVALID_CURSOR"

  Scenario: Reject using an active-only cursor with archived filtering changed
    Given I created 3 active routines for pagination
    And I listed active routines with limit 2 and saved the next cursor
    When I use the saved cursor while including archived routines
    Then the response status is 400
    And the problem code is "INVALID_CURSOR"

  Scenario Outline: Validate routine page size
    When I list active routines with limit <limit>
    Then the response status is 400
    And the problem code is "VALIDATION_FAILED"

    Examples:
      | limit |
      | 0     |
      | 101   |
