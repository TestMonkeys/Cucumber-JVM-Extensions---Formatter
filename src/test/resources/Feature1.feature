Feature: Test Feature 1

  Background: Background Sample
    Given something
    Given something
    Given something

  @tag1
  Scenario: scenario a1
    Given something
    When action
    Then result

  @tag2 @tag5
  Scenario: scenario b1
    Given something
    When action
    Then result

  Scenario Outline: Sample scenario outline
    Given something
    When action <column1>
    Then result <column2>
    Examples:
      | column1 | column2 |
      | value1  | value2  |
      | value2  | value3  |