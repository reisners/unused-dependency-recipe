package com.yourorg

import com.yourorg.table.UnusedDependencyReport
import com.yourorg.table.UnusedDependencyReport.DependencyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.java.Assertions.*
import org.openrewrite.java.JavaParser
import org.openrewrite.java.marker.JavaSourceSet
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.Assertions
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.maven.Assertions.pomXml
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.SourceSpec
import org.openrewrite.test.UncheckedConsumer
import java.util.function.Consumer

internal class UnusedDependenciesKotlinTest : RewriteTest {

    val jssWithGuava: JavaSourceSet = JavaSourceSet.build("main", JavaParser.dependenciesFromClasspath("guava"))

    override fun defaults(spec: RecipeSpec) {
        spec
            .recipe(UnusedDependencies())
            .parser(
                KotlinParser.Builder()
                    .classpath(
                        "guava"
                    )
            )
    }

    @Test
    fun shouldFindUnusedMavenDependencies() {
        rewriteRun(
             { spec ->
                spec.dataTable<UnusedDependencyReport.Row>(
                    UnusedDependencyReport.Row::class.java,
                    UncheckedConsumer { rows ->
                        assertThat<UnusedDependencyReport.Row>(rows).containsExactly(
                            UnusedDependencyReport.Row(DependencyType.MAVEN, "com.google.guava", "guava")
                        )
                    })
            },
            mavenProject(
                "project",  //language=XML
                pomXml(
                    """
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
              
              """.trimIndent()
                )
            ),
            srcMainJava(
                org.openrewrite.java.Assertions.java( //language=java
                    """
                public class A {
                    private String s;
                }
                
                """.trimIndent(),
                    { spec -> spec.markers(jssWithGuava) }
                )
            )
        )
    }

    @Test
    fun shouldNotFindUsedMavenDependencies() {
        rewriteRun(
            mavenProject(
                "project",
                //language=XML
                pomXml(
                    """
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
                      """
                ),
                srcMainJava(
                    { spec -> sourceSet(spec, "main") },
                    Assertions.kotlin(
                        """
                                import com.google.common.collect.CompactHashSet
                                
                                public class A {
                                    val s = CompactHashSet<String>()
                                }
                              """
                            .trimIndent(),
                        { spec -> spec.markers(jssWithGuava) }
                    )
                )
            )
        )
    }

}
