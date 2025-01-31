package com.contentgrid.junit.jupiter.k8s.providers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class K3sTestcontainersClusterProvider implements KubernetesClusterProvider {

    public static final DockerImageName IMAGE_RANCHER_K3S = DockerImageName.parse("rancher/k3s");

    @NonNull
    protected final K3sContainer container;

    private final Map<String, String> mirrors = new HashMap<>();

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR)
    );

    public K3sTestcontainersClusterProvider() {
        this(new K3sContainer(IMAGE_RANCHER_K3S));
    }

    @Override
    public ProviderEvaluationResult evaluate() {
        if (!this.isDockerAvailable()) {
            return ProviderEvaluationResult.disabled("docker is not available");
        }

        return ProviderEvaluationResult.enabled();
    }

    @Override
    public KubernetesProviderResult start() {

        log.info("Starting k3s: {}", String.join(" ", this.container.getCommandParts()));

        registriesConfigYaml(this.registriesConfig()).ifPresent(yaml -> {
            log.info("Configuring K3s registries.yaml:");
            log.info(yaml);
            this.container.withCopyToContainer(Transferable.of(yaml), "/etc/rancher/k3s/registries.yaml");
        });

        this.container.start();

        return new DelegatedKubernetesProviderResult(this.container.getKubeConfigYaml(), this::stop);
    }

    K3sRegistriesConfiguration registriesConfig() {
        var config = new K3sRegistriesConfiguration();
        this.mirrors.forEach((name, endpoint) -> {
            config.mirrors.put(name, new RegistryMirrorConfig(endpoint));
        });

        return config;
    }

    @SneakyThrows
    Optional<String> registriesConfigYaml(K3sRegistriesConfiguration config) {
        if (config.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(this.yamlMapper.writeValueAsString(config));
    }

    @Override
    public void stop() {
        log.debug("Stopping {}", this.container);
        this.container.close();
    }

    boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @RequiredArgsConstructor
    static class K3sProviderResult implements KubernetesProviderResult {

        @Getter
        @NonNull
        final String kubeConfigYaml;

        @NonNull
        final Closeable closeableDelegate;

        @Override
        public void close() throws Throwable {
            this.closeableDelegate.close();
        }
    }

    @Override
    public void addDockerRegistryMirror(@NonNull String name, @NonNull String endpoint) {
        if (this.container.isRunning()) {
            throw new IllegalStateException("container already running");
        }

        this.mirrors.put(name, endpoint);
    }

    /**
     * Get the host that this container may be reached on (may not be the local machine).
     *
     * @return docker host or ip address
     */
    protected static String getDockerHost() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }


    @Data
    static class K3sRegistriesConfiguration {

        @JsonInclude(Include.NON_EMPTY)
        Map<String, RegistryMirrorConfig> mirrors = new HashMap<>();

        @JsonIgnore
        boolean isEmpty() {
            return this.mirrors.isEmpty();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RegistryMirrorConfig {

        List<String> endpoint = new ArrayList<>();

        public RegistryMirrorConfig(@NonNull String endpoint) {
            this.endpoint.add(endpoint);
        }
    }
}
