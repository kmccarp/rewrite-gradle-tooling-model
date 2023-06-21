/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gradle.marker;

import lombok.Value;
import lombok.With;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.tree.MavenRepository;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Contains metadata about a Gradle Project. Queried from Gradle itself when the OpenRewrite build plugin runs.
 * Not automatically available on LSTs that aren't parsed through a Gradle plugin, so tests won't automatically have
 * access to this metadata.
 */
@SuppressWarnings("unused")
@Value
@With
public class GradleProject implements Marker, Serializable {
    UUID id;
    String name;
    String path;
    List<GradlePluginDescriptor> plugins;
    List<MavenRepository> mavenRepositories;
    List<MavenRepository> mavenPluginRepositories;
    Map<String, GradleDependencyConfiguration> nameToConfiguration;

    @Nullable
    public GradleDependencyConfiguration getConfiguration(String name) {
        return nameToConfiguration.get(name);
    }

    public List<GradleDependencyConfiguration> getConfigurations() {
        return new ArrayList<>(nameToConfiguration.values());
    }

    /**
     * List the configurations which extend from the given configuration.
     * Assuming a hierarchy like:
     * <pre>
     *     implementation
     *     |> compileClasspath
     *     |> runtimeClasspath
     *     |> testImplementation
     *        |> testCompileClasspath
     *        |> testRuntimeClasspath
     * </pre>
     *
     * When querying "implementation" with transitive is false this function will return [compileClasspath, runtimeClasspath, testImplementation].
     * When transitive is true this function will also return [testCompileClasspath, testRuntimeClasspath].
     */
    public List<GradleDependencyConfiguration> configurationsExtendingFrom(
            GradleDependencyConfiguration parentConfiguration,
            boolean transitive
    ) {
        List<GradleDependencyConfiguration> result = new ArrayList<>();
        for (GradleDependencyConfiguration configuration : nameToConfiguration.values()) {
            if (configuration == parentConfiguration) {
                continue;
            }
            for (GradleDependencyConfiguration extendsFrom : configuration.getExtendsFrom()) {
                if (extendsFrom == parentConfiguration) {
                    result.add(configuration);
                    if (transitive) {
                        result.addAll(configurationsExtendingFrom(configuration, true));
                    }
                }
            }
        }
        return result;
    }

    public GradleProject withNameToConfiguration(Map<String, GradleDependencyConfiguration> nameToConfiguration) {
        Map<String, GradleDependencyConfiguration> configurations = new HashMap<>(nameToConfiguration);
        for (GradleDependencyConfiguration gdc : configurations.values()) {
            List<GradleDependencyConfiguration> extendsFromList = new ArrayList<>(gdc.getExtendsFrom());
            boolean changed = false;
            for (int i = 0; i < extendsFromList.size(); i++) {
                GradleDependencyConfiguration extendsFrom = extendsFromList.get(i);
                if (configurations.get(extendsFrom.getName()) != extendsFrom) {
                    extendsFromList.set(i, configurations.get(extendsFrom.getName()));
                    changed = true;
                }
            }
            if (changed) {
                configurations.put(gdc.getName(), gdc.withExtendsFrom(extendsFromList));
            }
        }

        return new GradleProject(
                id,
                name,
                path,
                plugins,
                mavenRepositories,
                mavenPluginRepositories,
                configurations
        );
    }

    public static GradleProject fromToolingModel(org.openrewrite.gradle.toolingapi.GradleProject project) {
        return new GradleProject(
                UUID.randomUUID(),
                project.getName(),
                project.getPath(),
                project.getPlugins().stream()
                        .map(GradlePluginDescriptor::fromToolingModel)
                        .collect(Collectors.toList()),
                project.getMavenRepositories().stream()
                        .map(GradleProject::fromToolingModel)
                        .collect(Collectors.toList()),
                project.getMavenPluginRepositories().stream()
                        .map(GradleProject::fromToolingModel)
                        .collect(Collectors.toList()),
                GradleDependencyConfiguration.fromToolingModel(project.getNameToConfiguration())
        );
    }

    @Nullable
    static MavenRepository fromToolingModel(@Nullable org.openrewrite.gradle.toolingapi.MavenRepository mavenRepository) {
        if (mavenRepository == null) {
            return null;
        }
        return new MavenRepository(
                mavenRepository.getId(),
                mavenRepository.getUri(),
                mavenRepository.getReleases(),
                mavenRepository.getSnapshots(),
                mavenRepository.isKnownToExist(),
                mavenRepository.getUsername(),
                mavenRepository.getPassword(),
                mavenRepository.getDeriveMetadataIfMissing()
        );
    }
}
