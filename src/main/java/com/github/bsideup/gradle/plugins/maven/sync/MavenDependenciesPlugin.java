package com.github.bsideup.gradle.plugins.maven.sync;

import com.github.bsideup.gradle.plugins.maven.sync.model.MavenCoordinates;
import lombok.SneakyThrows;
import lombok.val;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class MavenDependenciesPlugin implements Plugin<Project> {

  // Use WeakHashMap, just in case of some cool daemon-based optimizations
  private static Map<Project, Map<MavenCoordinates, Set<Consumer<Project>>>> MODELS = Collections.synchronizedMap(new WeakHashMap<>());

  @Override
  @SneakyThrows
  public void apply(Project project) {
    val coordinates = MavenCoordinates.create(project);

    if (!coordinates.isPresent()) {
      project.getLogger().debug("Project {} has no pom.xml. Skipping...", project);
      return;
    }

    val model = MODELS.computeIfAbsent(project.getRootProject(), EffectiveModelBuilder.INSTANCE);
    for (val dependency : model.get(coordinates.get())) {
      dependency.accept(project);
    }
  }
}
