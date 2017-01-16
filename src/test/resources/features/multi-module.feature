Feature: Maven sync multi-module project

  Scenario: pom with excludes
    Given POM file "pom.xml":
    """
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>

        <groupId>org.example</groupId>
        <artifactId>example-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
        <packaging>pom</packaging>

        <modules>
          <module>modules/moduleA</module>
          <module>modules/moduleB</module>
        </modules>
      </project>
    """
    Given POM file "modules/moduleA/pom.xml":
    """
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>

        <artifactId>module-a</artifactId>
        <packaging>jar</packaging>

        <parent>
          <groupId>org.example</groupId>
          <artifactId>example-parent</artifactId>
          <version>1.0-SNAPSHOT</version>
          <relativePath>../../pom.xml</relativePath>
        </parent>
      </project>
    """
    Given POM file "modules/moduleB/pom.xml":
    """
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>

        <artifactId>module-b</artifactId>
        <packaging>jar</packaging>

        <parent>
          <groupId>org.example</groupId>
          <artifactId>example-parent</artifactId>
          <version>1.0-SNAPSHOT</version>
          <relativePath>../../pom.xml</relativePath>
        </parent>

        <dependencies>
          <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>module-a</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
            <exclusions>
              <exclusion>
                <groupId>com.bad.group</groupId>
              </exclusion>
            </exclusions>
          </dependency>
        </dependencies>
      </project>
    """
    And a build file:
    """
      plugins {
        id 'com.github.bsideup.maven-sync' apply false
      }

      allprojects {
        apply plugin: 'java'
        apply plugin: 'com.github.bsideup.maven-sync'
      }
    """
    And a file "settings.gradle":
    """
    include ':modules:moduleA'
    include ':modules:moduleB'
    """
    When I run Gradle
    Then Project ":modules:moduleB" contains dependencies:
      | testCompile | project(':modules:moduleA') |
    And The following dependencies are excluded from "testCompile project(':modules:moduleA')":
      | com.bad.group |
