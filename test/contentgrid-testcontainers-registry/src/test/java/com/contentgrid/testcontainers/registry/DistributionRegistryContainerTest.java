package com.contentgrid.testcontainers.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;

class DistributionRegistryContainerTest {

    @Test
    void testDefaultSetup() {
        try (var container = new DistributionRegistryContainer()) {
            container.waitingFor(new HttpWaitStrategy());
            container.start();

            assertThat(container.isRunning()).isTrue();

            var client = WebTestClient.bindToServer()
                    .baseUrl("http://localhost:%d".formatted(container.getMappedPort(5000)))
                    .build();

            client.get().uri("/v2/_catalog")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody().json("""
                            {
                                repositories: []
                            }
                            """);

            // this is an insecure registry by default
            // this can't be easily accessed through docker-java
            // testing push/pull from this repo is left as an exercise to the reader
        }
    }

    @Test
    void registryConfiguration() {
        try (var container = new DistributionRegistryContainer()) {
            container.withProxy("https://registry-1.docker.io");

            var yml = container.registryConfig();

            assertThat(yml).isEqualTo("""
                    version: 0.1
                    log:
                      level: info
                    http:
                      addr: :5000
                    storage:
                      filesystem:
                        rootdirectory: /var/lib/registry
                    proxy:
                      remoteurl: https://registry-1.docker.io
                    """);
        }
    }

    @Test
    void pullThroughCache() {
        try (var container = new DistributionRegistryContainer()) {
            container.withProxy("https://registry-1.docker.io");
            container.start();

            assertThat(container.isRunning()).isTrue();

            var client = WebTestClient.bindToServer()
                    .baseUrl("http://%s".formatted(container.getRegistry()))
                    .build();

            // we start with an empty repository
            client.get().uri("/v2/_catalog").exchange()
                    .expectStatus().isOk()
                    .expectBody().json("""
                            {
                                repositories: []
                            }
                            """);

            // fetch an image manifest using the rest api
            // proxy infrastructure should pull this transparently from https://registry-1.docker.io
            client.get().uri("/v2/library/alpine/manifests/latest")
                    .exchange()
                    .expectStatus().isOk();

            client.get().uri("/v2/_catalog").exchange()
                    .expectStatus().isOk()
                    .expectBody().json("""
                            {
                                repositories: ["library/alpine"]
                            }
                            """);
        }
    }

    @Test
    void pullThroughCache_usingMountAsPersistentCache() throws IOException {
        var tmpHostMount = Files.createTempDirectory("docker-registry-").toAbsolutePath().toString();
        try (var container = new DistributionRegistryContainer()) {
            container
                    .withProxy("https://registry-1.docker.io")
                    .withFileSystemBind(tmpHostMount, "/var/lib/registry", BindMode.READ_WRITE)
                    .start();

            var client = WebTestClient.bindToServer()
                    .baseUrl("http://%s".formatted(container.getRegistry()))
                    .build();

            // we start with an empty repository
            client.get().uri("/v2/_catalog").exchange()
                    .expectStatus().isOk()
                    .expectBody().json("""
                            {
                                repositories: []
                            }
                            """);

            // fetch an image manifest using the rest api
            // proxy infrastructure should pull this transparently from https://registry-1.docker.io
            client.get().uri("/v2/library/alpine/manifests/latest")
                    .exchange()
                    .expectStatus().isOk();
        }

        try (var container = new DistributionRegistryContainer()) {
            container
                    .withProxy("https://registry-1.docker.io")
                    .withFileSystemBind(tmpHostMount, "/var/lib/registry", BindMode.READ_WRITE)
                    .start();

            var client = WebTestClient.bindToServer()
                    .baseUrl("http://%s".formatted(container.getRegistry()))
                    .build();

            client.get().uri("/v2/_catalog").exchange()
                    .expectStatus().isOk()
                    .expectBody().json("""
                            {
                                repositories: ["library/alpine"]
                            }
                            """);
        }
    }
}