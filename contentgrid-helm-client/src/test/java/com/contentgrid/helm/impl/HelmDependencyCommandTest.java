package com.contentgrid.helm.impl;

import com.contentgrid.helm.Helm;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HelmDependencyCommandTest {

    @Test
    void listDependency(@TempDir Path workingDirectory) throws Exception {
        var helm = Helm.builder()
                .withWorkingDirectory(workingDirectory)
                .build();

        Files.writeString(workingDirectory.resolve("Chart.yaml"), """
                        apiVersion: v2
                        name: test
                                                
                        type: application
                        version: 1.2.3
                                                
                        appVersion: "5.6.7"
                                                
                        dependencies:
                          - name: keycloakx
                            repository: https://codecentric.github.io/helm-charts
                            version: "2.3.0"
                          - name: external-secrets
                            version: 0.20.2
                            repository: "https://charts.external-secrets.io/"
                          - name: cert-manager
                            repository: https://charts.jetstack.io/
                            version: v1.17.2
                        """,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);


        // Copy test charts to working directory
        var classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("com/contentgrid/helm/impl/list-dependency/charts");
        Assertions.assertThat(resource).isNotNull();

        Path sourceChartsDir = Path.of(resource.toURI());
        Path targetChartsDir = workingDirectory.resolve("charts");
        Files.createDirectories(targetChartsDir);

        try (var files = Files.list(sourceChartsDir)) {
            files.filter(path -> path.toString().endsWith(".tgz"))
                    .forEach(sourcePath -> {
                        try {
                            Path targetPath = targetChartsDir.resolve(sourcePath.getFileName());
                            Files.copy(sourcePath, targetPath);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to copy chart: " + sourcePath, e);
                        }
                    });
        }


        var result = helm.dependency().list();

        Assertions.assertThat(result)
                .hasSize(3)
                .satisfiesExactlyInAnyOrder(
                        dep -> {
                            Assertions.assertThat(dep.name()).isEqualTo("keycloakx");
                            Assertions.assertThat(dep.repository())
                                    .isEqualTo(URI.create("https://codecentric.github.io/helm-charts"));
                            Assertions.assertThat(dep.version()).isEqualTo("2.3.0");
                            Assertions.assertThat(dep.status()).isEqualTo("missing");
                        },
                        dep -> {
                            Assertions.assertThat(dep.name()).isEqualTo("external-secrets");
                            Assertions.assertThat(dep.repository())
                                    .isEqualTo(URI.create("https://charts.external-secrets.io/"));
                            Assertions.assertThat(dep.version()).isEqualTo("0.20.2");
                            Assertions.assertThat(dep.status()).isEqualTo("wrong version");
                        },
                        dep -> {
                            Assertions.assertThat(dep.name()).isEqualTo("cert-manager");
                            Assertions.assertThat(dep.repository())
                                    .isEqualTo(URI.create("https://charts.jetstack.io/"));
                            Assertions.assertThat(dep.version()).isEqualTo("v1.17.2");
                            Assertions.assertThat(dep.status()).isEqualTo("ok");
                        }
                );
    }

}