/*
 * Copyright 2024 the original author or authors.
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
package com.yourorg.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class UnusedDependencyReport extends DataTable<UnusedDependencyReport.Row> {

    public UnusedDependencyReport(Recipe recipe) {
        super(recipe,
                "Unused dependency report",
                "Unused dependencies.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Project",
                description = "Found in project")
        String project;

        @Column(displayName = "Dependency type",
                description = "Type of dependency")
        DependencyType dependencyType;

        @Column(displayName = "GroupId",
                description = "GroupId")
        String groupId;

        @Column(displayName = "ArtifactId",
                description = "ArtifactId")
        String artifactId;
    }

    public enum DependencyType {
        MAVEN,
        GRADLE
    }
}
