package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmClient;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HelmRepositoryCommandTest {

    @Test
    void emptyRepo(@TempDir Path helmTemp) throws Exception {
        var helm = HelmClient.builder()
                .withRepositoryConfig(Files.createFile(helmTemp.resolve("repo-config")))
                .build();

        Assertions.assertThat(helm.repository().list()).isEmpty();
    }

    @Test
    void listRepo(@TempDir Path helmTemp) throws Exception {
        var repoConfigPath = Files.createFile(helmTemp.resolve("repo-config"));
        var helm = HelmClient.builder()
                .withRepositoryConfig(repoConfigPath)
                .build();

        Files.writeString(repoConfigPath, """
                                repositories:
                                - name: codecentric
                                  url: https://codecentric.github.io/helm-charts
                        """,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Assertions.assertThat(helm.repository().list()).singleElement().satisfies(repo -> {
            Assertions.assertThat(repo.name()).isEqualTo("codecentric");
            Assertions.assertThat(repo.url()).isEqualTo("https://codecentric.github.io/helm-charts");
        });
    }

    @Test
    void addRepo(@TempDir Path helmTemp) throws Exception {
        var helm = HelmClient.builder()
                .withRepositoryConfig(Files.createFile(helmTemp.resolve("repo-config")))
                .build();

        Assertions.assertThat(helm.repository().list()).isEmpty();
        helm.repository().add("codecentric", URI.create("https://codecentric.github.io/helm-charts"));

        Assertions.assertThat(helm.repository().list()).singleElement().satisfies(repo -> {
            Assertions.assertThat(repo.name()).isEqualTo("codecentric");
            Assertions.assertThat(repo.url()).isEqualTo("https://codecentric.github.io/helm-charts");
        });
    }

}