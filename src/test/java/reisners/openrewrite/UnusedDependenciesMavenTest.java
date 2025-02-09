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

import reisners.openrewrite.table.UnusedDependencyReport;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.Java17Parser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static reisners.openrewrite.table.UnusedDependencyReport.DependencyType.MAVEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.kotlin.Assertions.srcMainKotlin;
import static org.openrewrite.maven.Assertions.pomXml;

class UnusedDependenciesMavenTest implements RewriteTest {

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
    void shouldFindUnusedMavenDependencies() {
        rewriteRun(
          spec -> spec.dataTable(UnusedDependencyReport.Row.class, rows -> assertThat(rows).containsExactly(
            new UnusedDependencyReport.Row("project", MAVEN, "org.apache.commons", "commons-lang3")
          )),
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
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>2.0.16</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                            <version>3.17.0</version>
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
        );
    }

    @Test
    void shouldNotFindUsedMavenDependencies() {
        AssertionError error = assertThrows(
          AssertionError.class,
          () -> rewriteRun(
            spec -> spec
              .dataTable(UnusedDependencyReport.Row.class, rows -> fail("should not occur")),
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
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>2.0.16</version>
                        </dependency>
                    </dependencies>
                </project>
                """.stripIndent()),
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
