package com.github.bsideup.gradle.plugins.maven.sync;

import com.github.bsideup.gradle.plugins.maven.sync.model.MavenCoordinates;
import lombok.SneakyThrows;
import lombok.val;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Files;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toMap;

public enum EffectiveModelBuilder implements Function<Project, Map<MavenCoordinates, Set<Consumer<Project>>>> {
  INSTANCE;

  @Override
  @SneakyThrows
  public Map<MavenCoordinates, Set<Consumer<Project>>> apply(Project rootProject) {
    val coordinatesToProjectName = rootProject.getAllprojects()
        .stream()
        .map(project -> new SimpleEntry<>(MavenCoordinates.create(project), project.getPath()))
        .filter(entry -> entry.getKey().isPresent())
        .collect(toMap(it -> it.getKey().get(), SimpleEntry::getValue));

    val tempFile = Files.createTempFile("effective-pom", ".xml");

    try {
      // TODO Maven Wrapper support
      new ProcessExecutor("mvn", "-q", "help:effective-pom", "-Doutput=" + tempFile.toAbsolutePath().toString())
          .directory(rootProject.getRootDir())
          .exitValueNormal()
          .readOutput(true)
          .execute();
    } catch (InvalidExitValueException e) {
      rootProject.getLogger().debug(e.getResult().outputString());
      throw new GradleException("Can't build an effective model from POM", e);
    }

    return Stream.of(new SAXBuilder().build(Files.newInputStream(tempFile)).getRootElement())
        .flatMap(it -> "project".equals(it.getName()) ? Stream.of(it) : it.getChildren().stream())
        .collect(toMap(
            MavenCoordinates::create,
            projectElement -> {
              val namespace = projectElement.getNamespace();
              Element dependencies = projectElement.getChild("dependencies", namespace);
              if (dependencies == null) {
                return emptySet();
              }
              return dependencies.getChildren("dependency", namespace)
                  .stream()
                  .map(dependency -> (Consumer<Project>) subProject -> {
                    val configuration = ((Function<String, String>) scope -> {
                      switch (scope) {
                        case "test":
                          return "testCompile";
                        default:
                          // TODO make it configurable?
                          return scope;
                      }
                    }).apply(dependency.getChildText("scope", namespace));

                    if (subProject.getConfigurations().getByName(configuration) == null) {
                      throw new UnknownConfigurationException(configuration);
                    }

                    val coordinates = MavenCoordinates.create(dependency);

                    final Object dependencyObject = Optional
                        .ofNullable(coordinatesToProjectName.get(coordinates))
                        .<Object>map(subProject::project)
                        .orElseGet(coordinates::toGradleNotation);

                    val moduleDependency = (ModuleDependency) subProject.getDependencies().add(configuration, dependencyObject);
                    dependency.getChildren("exclusions", namespace).stream()
                        .flatMap(it -> it.getChildren("exclusion", namespace).stream())
                        .forEach(exclusion -> {
                          val gradleExclusion = new HashMap<String, String>();

                          String group = exclusion.getChildText("groupId", namespace);
                          if (group != null) {
                            gradleExclusion.put("group", group);
                          }

                          String module = exclusion.getChildText("artifactId", namespace);
                          if (module != null) {
                            gradleExclusion.put("module", module);
                          }

                          moduleDependency.exclude(gradleExclusion);
                        });
                  })
                  .collect(Collectors.toSet());
            }));
  }
}
