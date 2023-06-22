/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.tree.MavenRepository;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Value
@With
public class GradleSettings implements Marker, Serializable {
    UUID id;
    List<MavenRepository> pluginRepositories;
    List<GradlePluginDescriptor> plugins;
    Map<String, FeaturePreview> featurePreviews;

    public boolean isFeatureEnabled(String name) {
        return featurePreviews.get(name).isEnabled();
    }

    public Set<FeaturePreview> getActiveFeatures() {
        return featurePreviews.values().stream()
                .filter(FeaturePreview::isActive)
                .collect(Collectors.toSet());
    }

    public static GradleSettings fromToolingModel(org.openrewrite.gradle.toolingapi.GradleSettings settings) {
        return new GradleSettings(
                UUID.randomUUID(),
                settings.getPluginRepositories().stream()
                        .map(GradleProject::fromToolingModel)
                        .collect(Collectors.toList()),
                settings.getPlugins().stream()
                        .map(GradlePluginDescriptor::fromToolingModel)
                        .collect(Collectors.toList()),
                fromToolingModel(settings.getFeaturePreviews())
        );
    }

    private static Map<String, FeaturePreview> fromToolingModel(Map<String, org.openrewrite.gradle.toolingapi.FeaturePreview> toolingFeaturePreviews) {
        Map<String, FeaturePreview> results = new HashMap<>();
        for (Map.Entry<String, org.openrewrite.gradle.toolingapi.FeaturePreview> featurePreviewEntry : toolingFeaturePreviews.entrySet()) {
            results.put(featurePreviewEntry.getKey(), FeaturePreview.fromToolingModel(featurePreviewEntry.getValue()));
        }
        return results;
    }
}