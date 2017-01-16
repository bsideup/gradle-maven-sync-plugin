Feature: Maven sync excludes

  Scenario: pom with excludes
    Given POM file with dependencies:
      | groupId   | artifactId | version | excludes                    |
      | org.slf4j | slf4j-api  | 1.7.22  | org.test:some-test, foo.bar |
    And a build file:
    """
      plugins {
        id 'java'
        id 'com.github.bsideup.maven-sync'
      }
    """
    When I run Gradle
    Then The following dependencies are excluded from "compile org.slf4j:slf4j-api:1.7.22":
      | org.test:some-test |
      | foo.bar            |
