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
package reisners.openrewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.Java17Parser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import reisners.openrewrite.table.UnusedDependencyReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.kotlin.Assertions.srcMainKotlin;
import static reisners.openrewrite.table.UnusedDependencyReport.DependencyType.GRADLE;

class UnusedDependenciesGradleTest implements RewriteTest {

    private final String[] artifactNames = {"guava", "slf4j-api", "commons-lang3"};

    final JavaSourceSet jssWithDependencies = JavaSourceSet.build("main", JavaParser.dependenciesFromClasspath(artifactNames));

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnusedDependencies())
          .parser(
            new Java17Parser.Builder()
              .classpath(artifactNames
              )
          )
          .parser(KotlinParser.builder().classpath(artifactNames));
    }

    @Test
    void shouldFindUnusedGradleDependencies() {
        rewriteRun(
          spec -> {
              spec.beforeRecipe(withToolingApi());
              spec.dataTable(UnusedDependencyReport.Row.class, rows -> assertThat(rows).containsExactly(
                new UnusedDependencyReport.Row("project", GRADLE, "com.google.guava", "guava"),
                new UnusedDependencyReport.Row("project", GRADLE, "org.slf4j", "slf4j-api")
              ));
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
                  implementation 'org.slf4j:slf4j-api:2.0.16'
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
              spec -> spec.markers(jssWithDependencies)
            )
          )
        );
    }

    @Test
    void shouldNotFindUsedGradleDependencies() {
        AssertionError error = assertThrows(
          AssertionError.class,
          () -> rewriteRun(
            spec -> spec
              .dataTable(UnusedDependencyReport.Row.class, rows -> fail("should not occur")),
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
                  implementation 'org.slf4j:slf4j-api:2.0.16'
              }
              """),
              srcMainJava(
                java(
                  //language=java
                  """
                    import com.google.common.collect.Collections2;
                    
                    public class A {
                        private Collection<List<String>> s = Collections2.permutations(java.util.Arrays.asList("a", "b", "c"));
                    }
                    """.stripIndent(),
                  spec -> spec.markers(jssWithDependencies)
                )
              ),
              srcMainKotlin(
                kotlin( //language=kotlin
                  """
                  import org.slf4j.Logger
                  data class B(val logger: Logger, val s: String)
                  """.stripIndent(),
                  spec -> spec.markers(jssWithDependencies)
                )
              )
            )
          )
        );
        assertThat(error.getMessage()).contains("No data table found");
    }

}
