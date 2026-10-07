@e2e
Feature: Exercise log API

  Scenario: Create an exercise log with a business-language request
    When I create an exercise log:
      | exerciseName         | sets[0].setNumber | sets[0].weightKg | sets[0].reps |
      | Cucumber Bench Press | 1                | 60               | 10           |
    Then the exercise log is created for "Cucumber Bench Press"

  @e2e
  Scenario: Read a created exercise log
    When I create an exercise log:
      | exerciseName         | sets[0].setNumber | sets[0].weightKg | sets[0].reps |
      | Cucumber Front Squat  | 1                | 70               | 8            |
    And I request the created exercise log
    Then the exercise log response contains "Cucumber Front Squat" with volume 560.00

  @e2e
  Scenario: List distinct exercise names
    When I create an exercise log:
      | exerciseName         | sets[0].setNumber | sets[0].weightKg | sets[0].reps |
      | Cucumber Deadlift     | 1                | 100              | 5            |
    And I request the distinct exercise names
    Then the distinct exercise names include "Cucumber Deadlift"

  @e2e
  Scenario: Sort exercise logs by highest single-set volume
    Given I create three logs for a sorting exercise
    When I request the top two logs for the sorting exercise
    Then the sorting response orders volumes 640.00 then 600.00 and excludes 500.00
