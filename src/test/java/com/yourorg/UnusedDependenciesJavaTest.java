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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.Java17Parser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static com.yourorg.table.UnusedDependencyReport.DependencyType.GRADLE;
import static com.yourorg.table.UnusedDependencyReport.DependencyType.MAVEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class UnusedDependenciesJavaTest implements RewriteTest {

    final JavaSourceSet jssWithGuava = JavaSourceSet.build("main", JavaParser.dependenciesFromClasspath("guava"));

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnusedDependencies())
          .parser(
            new Java17Parser.Builder()
              .classpath(
                "guava"
              )
          );
    }

    @Test
    void shouldFindUnusedMavenDependencies() {
        rewriteRun(
          spec -> assertDataTable(spec, MAVEN),
          mavenProject("project",
            //language=XML
            pomXml("""
              <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.yourorg</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.1-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.google.guava</groupId>
                          <artifactId>guava</artifactId>
                          <version>33.3.1-jre</version>
                      </dependency>
                  </dependencies>
              </project>
              """)
          ),
          srcMainJava(
            java(
              //language=java
              """
                public class A {
                    private String s;
                }
                """,
              spec -> spec.markers(jssWithGuava)
            )
          )
        );
    }

    @Test
    void shouldNotFindUsedMavenDependencies() {
        AssertionError error = assertThrows(
          AssertionError.class,
          () -> rewriteRun(
            spec -> spec
              .dataTable(UnusedDependencyReport.Row.class, rows -> {
                  assertThat(rows).isNullOrEmpty();
              }),
            mavenProject("project",
              //language=XML
              pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.yourorg</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.google.guava</groupId>
                            <artifactId>guava</artifactId>
                            <version>33.3.1-jre</version>
                        </dependency>
                    </dependencies>
                </project>
                """),
              srcMainJava(
                java(
                  //language=java
                  """
                    import com.google.common.collect.Collections2;
                    
                    public class A {
                        private Collection<List<String>> s = Collections2.permutations(java.util.Arrays.asList("a", "b", "c"));
                    }
                    """,
                  spec -> spec.markers(jssWithGuava)
                )
              )
            )
          )
        );
    }

    @Test
    void shouldProcessGradleProject() {
        rewriteRun(
          spec ->
              spec.beforeRecipe(withToolingApi()),
            //language=groovy
            buildGradle("""
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation 'com.google.guava:guava:33.3.1-jre'
              }
              """)
        );
    }

    @Test
    void shouldFindUnusedGradleDependencies() {
        rewriteRun(
          spec -> {
              spec.beforeRecipe(withToolingApi());
              assertDataTable(spec, GRADLE);
          },
          mavenProject("project",
            //language=groovy
            buildGradle("""
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  implementation 'com.google.guava:guava:33.3.1-jre'
              }
              """)
          ),
          srcMainJava(
            java(
              //language=java
              """
                public class A {
                    private String s;
                }
                """,
              spec -> spec.markers(jssWithGuava)
            )
          )
        );
    }

    private static @NotNull RecipeSpec assertDataTable(RecipeSpec spec, UnusedDependencyReport.DependencyType dependencyType) {
        return spec.dataTable(UnusedDependencyReport.Row.class, rows -> {
            assertThat(rows).containsExactly(
              new UnusedDependencyReport.Row(dependencyType, "com.google.guava", "guava")
            );
        });
    }

}
