Feature: Maven sync

  Scenario: simple project
    Given a project directory
    And POM file with dependencies:
      | groupId       | artifactId        | version | scope    |
      | org.slf4j     | slf4j-api         | 1.7.22  |          |
      | javax.servlet | javax.servlet-api | 3.1.0   | provided |
      | junit         | junit             | 4.12    | test     |
      | org.assertj   | assertj-core      | 3.6.1   | test     |
    And a build file:
    """
      plugins {
        id 'java'
        id 'com.github.bsideup.maven-sync' apply false
      }

      configurations {
        provided
      }

      apply plugin: 'com.github.bsideup.maven-sync'
    """
    When I run Gradle
    Then Configuration "compile" of project ":" contains dependencies:
      | org.slf4j:slf4j-api:1.7.22 |
    And Configuration "provided" of project ":" contains dependencies:
      | javax.servlet:javax.servlet-api:3.1.0 |
    And Configuration "testCompile" of project ":" contains dependencies:
      | junit:junit:4.12               |
      | org.assertj:assertj-core:3.6.1 |
