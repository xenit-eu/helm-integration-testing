package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmClient;
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
        var helm = HelmClient.builder()
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
                        """,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        var result = helm.dependency().list();

        Assertions.assertThat(result).singleElement().satisfies(dep -> {
            Assertions.assertThat(dep.getName()).isEqualTo("keycloakx");
            Assertions.assertThat(dep.getRepository()).isEqualTo(URI.create("https://codecentric.github.io/helm-charts"));
            Assertions.assertThat(dep.getVersion()).isEqualTo("2.3.0");
        });
    }

}