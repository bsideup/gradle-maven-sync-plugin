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
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class GradleStepdefs {

  Path projectDir;
  BuildResult result;
  JsonObject report;
  Scenario scenario;

  @Before
  public void before(Scenario scenario) {
    this.scenario = scenario;
  }

  @Given("^a project directory$")
  public void projectDirectory() throws Throwable {
    projectDir = Files.createTempDirectory("gradle-maven-sync-test-project");
  }

  @And("^a build file:$")
  public void aBuildFile(String body) throws Throwable {
    URL reportScript = GradleStepdefs.class.getResource("/report.gradle");
    Files.write(projectDir.resolve("build.gradle"), (body + "\nrootProject.apply(from: '" + reportScript.getFile() + "')").getBytes());
  }

  @When("^I run Gradle$")
  public void iRunGradleWithTheArguments() throws Throwable {
    result = GradleRunner.create()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withArguments("--offline", "dependencies")
        .build();

    Path reportFile = projectDir.resolve("report.json");
    report = new Gson().fromJson(Files.newBufferedReader(reportFile), JsonElement.class).getAsJsonObject();
    scenario.embed(Files.readAllBytes(reportFile), "application/json");
  }

  @Then("^The output contains \"([^\"]*)\"$")
  public void theOutputContainers(String message) throws Throwable {
    assertThat(result.getOutput()).contains(message);
  }

  @And("^The outcome for \"([^\"]*)\" is ([^\"]*)")
  public void theOutcomeIsSUCCESS(String task, TaskOutcome outcome) throws Throwable {
    assertThat(result.task(task).getOutcome()).isEqualTo(outcome);
  }

  @And("^POM file with dependencies:$")
  public void pomFileWithDependencies(List<Dependency> dependencies) throws Throwable {
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

  @And("^Project \"([^\"]*)\" contains dependencies:$")
  public void configurationContainsDependencies(String projectName, List<List<String>> dependencies) throws Throwable {
    Stream<List<String>> entries = report.getAsJsonObject(projectName)
        .entrySet()
        .stream()
        .flatMap(configurationEntry -> StreamSupport
            .stream(configurationEntry.getValue().getAsJsonArray().spliterator(), false)
            .map(JsonObject.class::cast)
            .map(dependency -> asList(
                configurationEntry.getKey(),
                String.format(
                    "%s:%s:%s",
                    dependency.get("group").getAsString(),
                    dependency.get("name").getAsString(),
                    dependency.get("version").getAsString()
                )
            )));

    assertThat(entries)
        .containsOnly(dependencies.toArray(new List[]{}));
  }
}
