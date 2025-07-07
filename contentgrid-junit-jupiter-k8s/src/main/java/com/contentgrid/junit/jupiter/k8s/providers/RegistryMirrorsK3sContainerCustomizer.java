package com.contentgrid.junit.jupiter.k8s.providers;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures registry mirrors
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class RegistryMirrorsK3sContainerCustomizer implements K3sContainerCustomizer {

    @NonNull
    private final Map<String, String> mirrors;

    public RegistryMirrorsK3sContainerCustomizer() {
        this(Map.of());
    }

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR)
    );

    public RegistryMirrorsK3sContainerCustomizer withMirror(String name, String endpoint) {
        var mirrorsCopy = new HashMap<>(mirrors);
        if(mirrorsCopy.putIfAbsent(name, endpoint) != null) {
            throw new IllegalArgumentException("Mirror %s is already registered".formatted(name));
        }
        return new RegistryMirrorsK3sContainerCustomizer(mirrorsCopy);
    }

    @Override
    public void customize(K3sContainer container) {
        createRegistriesYaml().ifPresent(yaml -> {
            log.info("Configuring K3S registry mirrors");
            container.withCopyToContainer(Transferable.of(yaml), "/etc/rancher/k3s/registries.yaml");
        });
    }

    @SneakyThrows(JsonProcessingException.class)
    Optional<String> createRegistriesYaml() {
        if(mirrors.isEmpty()) {
            return Optional.empty();
        }
        var config = new K3sRegistriesConfiguration();
        this.mirrors.forEach((name, endpoint) -> {
            config.mirrors.put(name, new RegistryMirrorConfig(endpoint));
        });

        return Optional.of(yamlMapper.writeValueAsString(config));
    }

    @Data
    static class K3sRegistriesConfiguration {

        @JsonInclude(Include.NON_EMPTY)
        Map<String, RegistryMirrorConfig> mirrors = new HashMap<>();

    }

    @Data
    @NoArgsConstructor
    static class RegistryMirrorConfig {

        List<String> endpoint = new ArrayList<>();

        public RegistryMirrorConfig(@NonNull String endpoint) {
            this.endpoint.add(endpoint);
        }
    }
}
