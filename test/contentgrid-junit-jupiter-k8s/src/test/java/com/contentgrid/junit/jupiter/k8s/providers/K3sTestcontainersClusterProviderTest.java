package com.contentgrid.junit.jupiter.k8s.providers;

import static org.assertj.core.api.Assertions.assertThat;


import lombok.RequiredArgsConstructor;
import com.contentgrid.junit.jupiter.k8s.providers.K3sTestcontainersClusterProvider.K3sRegistriesConfiguration;
import org.junit.jupiter.api.Test;

class K3sTestcontainersClusterProviderTest {

    @Test
    void testRegistryCache() {
        try (var k3s = new K3sTestcontainersClusterProvider()) {
            k3s.addDockerRegistryMirror("docker.io", "http://localhost:5000");
            k3s.addDockerRegistryMirror("quay.io", "https://mirror.example.com/");

            var config = k3s.registriesConfig();
            var yaml = k3s.registriesConfigYaml(config);

            assertThat(yaml).hasValue("""
                    mirrors:
                      docker.io:
                        endpoint:
                          - "http://localhost:5000"
                      quay.io:
                        endpoint:
                          - "https://mirror.example.com/"
                    """);
        }
    }

    @Test
    void testEmptyConfig() {
        try (var k3s = new K3sTestcontainersClusterProvider()) {
            var config = new K3sRegistriesConfiguration();
            var yaml = k3s.registriesConfigYaml(config);

            assertThat(yaml).isEmpty();
        }
    }


    @Test
    void testEvaluation() {
        // test forces/fakes a docker daemon is available
        try (var provider = new TestK3sTestcontainersClusterProvider(true)) {
            assertThat(provider.evaluate().isEnabled()).isTrue();
        }
    }

    @Test
    void testEvalWithoutDocker() {
        // test forces/fakes a docker daemon is NOT available
        try (var provider = new TestK3sTestcontainersClusterProvider(false)) {
            assertThat(provider.evaluate().isEnabled()).isFalse();
        }
    }

    @RequiredArgsConstructor
    static class TestK3sTestcontainersClusterProvider extends K3sTestcontainersClusterProvider {
        private final boolean dockerAvailable;

        @Override
        public boolean isDockerAvailable() {
            return dockerAvailable;
        }
    }
}