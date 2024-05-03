package com.contentgrid.junit.jupiter.helm;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.helm.Helm;
import java.nio.file.Files;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

class HelmTestExtensionTest {

    @Nested
    class HelmTestChartAttributeTest {

        @Nested
        @HelmTest(chart = "src/test/resources/fixtures/app")
        class RelativePathToChart {

            @Test
            void verifyChartCopied(Helm helm) {
                assertThat(helm.dependency().list()).singleElement().satisfies(dep -> {
                    assertThat(dep.name()).isEqualTo("keycloakx");
                });
            }
        }

        @Nested
        @HelmTest(chart = "classpath:fixtures/app")
        class ClasspathChart {

            @Test
            void verifyChartCopied(Helm helm) {
                assertThat(helm.dependency().list()).singleElement().satisfies(dep -> {
                    assertThat(dep.name()).isEqualTo("keycloakx");
                });
            }
        }
        @Test
        void findChartPathDefaultsToEmptyOptional() {
            var path = HelmTestExtension.findChartPath(this.getClass(), "");
            assertThat(path).isEmpty();
        }

        @Test
        void findRelativePathChart() {
            var path = HelmTestExtension.findChartPath(this.getClass(), "src/test/resources/fixtures/app");
            assertThat(path).isPresent().hasValueSatisfying(location -> {
                assertThat(Files.isDirectory(location)).isTrue();
            });
        }

        @Test
        void relativePathDirectlyToChartYaml() {
            var path = HelmTestExtension.findChartPath(this.getClass(), "src/test/resources/fixtures/app/Chart.yaml");
            assertThat(path).isPresent().hasValueSatisfying(location -> {
                assertThat(Files.isDirectory(location)).isTrue();
            });
        }

        @Test
        void classpathChart() {
            var path = HelmTestExtension.findChartPath(this.getClass(), "classpath:fixtures/app");
            assertThat(path).isPresent().hasValueSatisfying(location -> {
                assertThat(Files.isDirectory(location)).isTrue();
            });
        }

        @Test
        void classpathChartDirectlyToChartYaml() {
            var path = HelmTestExtension.findChartPath(this.getClass(), "classpath:fixtures/app/Chart.yaml");
            assertThat(path).isPresent().hasValueSatisfying(location -> {
                assertThat(Files.isDirectory(location)).isTrue();
            });
        }

        @Test
        void nonExistingChartShouldThrow() {
            assertThatThrownBy(() -> HelmTestExtension.findChartPath(this.getClass(), "non-existing"))
                    .isInstanceOf(ExtensionConfigurationException.class);
        }

    }

    @Nested
    class AddChartRepositories {

        @Nested
        @HelmTest(chart = "classpath:fixtures/app")
        class DefaultWithChart {

            @Test
            void verify(Helm helm) {
                assertThat(helm.repository().list()).hasSize(1);
            }
        }

        @Nested
        @HelmTest(chart = "classpath:fixtures/app", addChartRepositories = false)
        class DisabledAddRepositoriesWithChart {

            @Test
            void verify(Helm helm) {
                assertThat(helm.repository().list()).isEmpty();
            }
        }
    }

}