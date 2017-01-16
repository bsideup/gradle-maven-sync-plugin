package steps;

import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import gherkin.deps.com.google.gson.Gson;
import gherkin.deps.com.google.gson.JsonElement;
import gherkin.deps.com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class GradleStepdefs {

  Path projectDir = ((Supplier<Path>) () -> {
    try {
      return Files.createTempDirectory("gradle-maven-sync-test-project");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }).get();

  BuildResult result;
  JsonObject report;
  Scenario scenario;
  String projectName = ":";

  @Before
  public void before(Scenario scenario) {
    this.scenario = scenario;
  }

  @And("^a build file:$")
  public void aBuildFile(String body) throws Throwable {
    URL reportScript = GradleStepdefs.class.getResource("/report.gradle");
    aFile("build.gradle", (body + "\nrootProject.apply(from: '" + reportScript.getFile() + "')"));
  }

  @And("^a file \"([^\"]*)\":$")
  public void aFile(String file, String body) throws Throwable {
    Files.write(projectDir.resolve(file), StringUtils.trim(StringGroovyMethods.stripIndent((CharSequence) body)).getBytes());
  }

  @When("^I run Gradle$")
  public void iRunGradle() throws Throwable {
    result = GradleRunner.create()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withArguments("--offline", "-q", "--stacktrace", "doReport")
        .build();

    scenario.write(result.getOutput());

    Path reportFile = projectDir.resolve("report.json");
    String reportString = new String(Files.readAllBytes(reportFile));
    report = new Gson().fromJson(reportString, JsonElement.class).getAsJsonObject();
    scenario.write(reportString);
  }

  @Then("^The output contains \"([^\"]*)\"$")
  public void theOutputContainers(String message) throws Throwable {
    assertThat(result.getOutput()).contains(message);
  }

  @And("^The outcome for \"([^\"]*)\" is ([^\"]*)")
  public void theOutcomeIs(String task, TaskOutcome outcome) throws Throwable {
    assertThat(result.task(task).getOutcome()).isEqualTo(outcome);
  }

  @And("^POM file with dependencies:$")
  public void pomFileWithDependencies(List<MavenDependencySpec> dependencies) throws Throwable {
    for (MavenDependencySpec dependency : dependencies) {
      dependency.setExcludes(dependency.excludes);
    }

    Model model = new Model();
    model.setModelVersion("4.0.0");
    model.setGroupId("com.example");
    model.setArtifactId("example-artifact");
    model.setVersion("1.0.0-SNAPSHOT");
    model.setPackaging("pom");
    dependencies.forEach(model::addDependency);

    Path pomFile = projectDir.resolve("pom.xml");
    new MavenXpp3Writer().write(Files.newBufferedWriter(pomFile), model);
  }

  @Given("^POM file \"([^\"]*)\":$")
  public void pomFile(String path, String body) throws Throwable {
    Path file = projectDir.resolve(path);
    Files.createDirectories(file.getParent());
    aFile(file.toString(), body);

    new MavenXpp3Reader().read(Files.newBufferedReader(file));
  }

  @And("^Project \"([^\"]*)\" contains dependencies:$")
  public void configurationContainsDependencies(String projectName, List<List<String>> dependencies) throws Throwable {
    this.projectName = projectName;
    JsonObject reportForProject = report.getAsJsonObject(projectName);
    assertThat(reportForProject).describedAs("Report for " + projectName).isNotNull();
    Stream<List<String>> entries = reportForProject
        .entrySet()
        .stream()
        .flatMap(configurationEntry -> StreamSupport
            .stream(configurationEntry.getValue().getAsJsonArray().spliterator(), false)
            .map(JsonObject.class::cast)
            .map(dependency -> asList(
                configurationEntry.getKey(),
                dependency.get("coordinates").getAsString()
            )));

    assertThat(entries)
        .containsOnly(dependencies.toArray(new List[]{}));
  }

  @Then("^The following dependencies are excluded from \"([^\"]*) ([^\"]*)\":$")
  public void theFollowingDependenciesAreExcludedFrom(String configuration, String from, List<String> exclusions) throws Throwable {
    JsonObject reportForProject = report.getAsJsonObject(projectName);
    assertThat(reportForProject).describedAs("Report for " + projectName).isNotNull();
    for (JsonElement dependency : reportForProject.getAsJsonArray(configuration)) {
      JsonObject dependencyObject = dependency.getAsJsonObject();
      if (from.equals(dependencyObject.get("coordinates").getAsString())) {
        assertThat(dependencyObject.getAsJsonArray("excludes"))
            .extracting(JsonObject.class::cast)
            .extracting(it -> {
              String group = it.get("group").getAsString();
              JsonElement module = it.get("module");
              if (module.isJsonNull()) {
                return group;
              }
              return group + ":" + module.getAsString();
            })
            .containsAll(exclusions);
        break;
      }
    }
  }

  public static class MavenDependencySpec extends Dependency {

    private String excludes;

    // Make Cucumber happy
    public void setExcludes(String exclusions) {
      if (exclusions == null) {
        return;
      }

      setExclusions(
          Stream.of(exclusions.split(","))
              .map(String::trim)
              .map(exclusion -> {
                String[] parts = exclusion.split(":");
                Exclusion result = new Exclusion();

                result.setGroupId(parts[0]);

                if (parts.length == 2) {
                  result.setArtifactId(parts[1]);
                }

                return result;
              })
              .collect(Collectors.toList())
      );
    }
  }
}
