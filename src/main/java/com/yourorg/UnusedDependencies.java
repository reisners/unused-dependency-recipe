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
package com.yourorg;

import com.yourorg.table.UnusedDependencyReport;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.tree.*;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class UnusedDependencies extends ScanningRecipe<UnusedDependencies.Accumulator> {

    transient UnusedDependencyReport report = new UnusedDependencyReport(this);

    @Override
    public String getDisplayName() {
        return "Find unused dependencies";
    }

    @Override
    public String getDescription() {
        return "Scans through source code collecting references to types and methods, identifying dependencies in Maven or Gradle build files that are not used.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    acc.recordTypesInUse((JavaSourceFile) tree);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                JavaProject javaProject = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (javaProject == null) {
                    return tree;
                }
                MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                if (mrr != null) {
                    List<ResolvedDependency> dependencies = mrr.getDependencies().get(Scope.Test);
                    dependencies.stream()
                            .filter(dep -> dep.getDepth() == 0)
                            .forEach(
                                dependency -> {
                                    GroupArtifact ga = dependency.getGav().asGroupArtifact();
                                    if (!acc.isInUse(javaProject, ga)) {
                                        System.out.println("Found unused maven dependency: " + ga);
                                        report.insertRow(ctx, new UnusedDependencyReport.Row(
                                                javaProject.getProjectName(),
                                                UnusedDependencyReport.DependencyType.MAVEN,
                                                ga.getGroupId(),
                                                ga.getArtifactId()));

                                    }
                                }
                            );
                }
                GradleProject gp = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
                if (gp != null) {
                    GradleDependencyConfiguration testRuntimeConfiguration = gp.getConfiguration("testRuntimeClasspath");
                    if (testRuntimeConfiguration != null) {
                        for (Dependency dependency : testRuntimeConfiguration.getRequested()) {
                            GroupArtifact ga = dependency.getGav().asGroupArtifact();
                            if (!acc.isInUse(javaProject, ga)) {
                                System.out.println("Found unused gradle dependency: " + ga);
                                report.insertRow(ctx, new UnusedDependencyReport.Row(
                                        javaProject.getProjectName(),
                                        UnusedDependencyReport.DependencyType.GRADLE,
                                        ga.getGroupId(),
                                        ga.getArtifactId()));
                            }
                        }
                    }

                }
                return tree;
            }
        };
    }

    public static class Accumulator {
        private final Map<JavaProject, Set<String>> projectToTypesInUse = new HashMap<>();
        private final Map<String, GroupArtifact> typeFqnToGA = new HashMap<>();

        public boolean isInUse(JavaProject project, GroupArtifact ga) {
            Set<String> typesInUse = projectToTypesInUse.computeIfAbsent(project, k -> new HashSet<>());
            for (String type : typesInUse) {
                if (ga.equals(typeFqnToGA.get(type))) {
                    return true;
                }
            }
            return false;
        }

        public void recordTypesInUse(JavaSourceFile cu) {
            TypesInUse types = cu.getTypesInUse();
            JavaProject javaProject = cu.getMarkers().findFirst(JavaProject.class).orElse(null);
            JavaSourceSet javaSourceSet = cu.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
            if (javaSourceSet == null || javaProject == null) {
                return;
            }
            recordTypesInUse(types, javaProject, javaSourceSet);
        }

        public void recordTypesInUse(TypesInUse types, JavaProject javaProject, JavaSourceSet javaSourceSet) {
            projectToTypesInUse.compute(javaProject, (k, v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                types.getTypesInUse().stream()
                        .filter(JavaType.FullyQualified.class::isInstance)
                        .map(JavaType.FullyQualified.class::cast)
                        .map(JavaType.FullyQualified::getFullyQualifiedName)
                        .forEach(v::add);
                return v;
            });
            for (Map.Entry<String, List<JavaType.FullyQualified>> gavToTypes : javaSourceSet.getGavToTypes().entrySet()) {
                String[] gav = gavToTypes.getKey().split(":");
                String group = gav[0];
                String artifact = gav[1];
                GroupArtifact ga = new GroupArtifact(group, artifact);
                for (JavaType.FullyQualified type : gavToTypes.getValue()) {
                    String fqn = type.getFullyQualifiedName();
                    typeFqnToGA.put(fqn, ga);
                }
            }
        }
    }

}
