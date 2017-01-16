package com.github.bsideup.gradle.plugins.maven.sync.model;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.val;
import org.gradle.api.Project;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Value
@EqualsAndHashCode(exclude = "version")
public class MavenCoordinates {

  String groupId;
  String artifactId;
  String version;
  // TODO classifier

  public static Optional<MavenCoordinates> create(Project project) {
    return Optional.of(project.file("pom.xml"))
        .filter(File::exists)
        .flatMap(pomFile -> {
          try {
            return Optional.of(new SAXBuilder().build(pomFile).getRootElement());
          } catch (JDOMException | IOException e) {
            project.getLogger().warn("Failed to parse pom.xml", e);
            return Optional.empty();
          }
        })
        .map(MavenCoordinates::create);
  }

  public static MavenCoordinates create(Element rootElement) {
    val namespace = rootElement.getNamespace();

    Function<String, String> getValue = key ->
        Optional
            .ofNullable(rootElement.getChildText(key, namespace))
            .filter(Objects::nonNull)
            .filter(it -> it.trim().length() > 0)
            .orElseGet(() -> {
              Element parent = rootElement.getChild("parent", namespace);
              return parent != null ? parent.getChildText(key, namespace) : null;
            });

    return new MavenCoordinates(
        getValue.apply("groupId"),
        rootElement.getChildText("artifactId", namespace),
        getValue.apply("version")
    );
  }

  public String toGradleNotation() {
    return String.format(
        "%s:%s:%s",
        getGroupId(),
        getArtifactId(),
        getVersion()
    );
  }
}
