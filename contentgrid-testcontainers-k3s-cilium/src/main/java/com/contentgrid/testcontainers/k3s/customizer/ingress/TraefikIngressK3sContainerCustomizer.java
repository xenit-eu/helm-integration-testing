package com.contentgrid.testcontainers.k3s.customizer.ingress;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import com.contentgrid.testcontainers.k3s.customizer.WaitStrategyCustomizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports.Binding;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.With;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;

/**
 * Installs <a href="https://traefik.io/">Traefik</a> as an ingress controller,
 * with a fixed binding for HTTP to port 80 on the host.
 */
@AllArgsConstructor
public class TraefikIngressK3sContainerCustomizer implements K3sContainerCustomizer {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR)
    );

    public TraefikIngressK3sContainerCustomizer() {
        this(Map.of(
                "logs.access.enabled", true,
                "ports.web.nodePort", 32080
        ));
    }

    /**
     * Helm values for traefik installation.
     * <p>
     * The keys can be dot-separated, which is automatically expanded into a valid tree structure
     */
    @With
    @NonNull
    private final Map<String, Object> helmValues;

    /**
     * Add an additional helm value for traefik installation
     * @param key The helm value key (can be dot-separated)
     * @param value The value to use
     */
    public TraefikIngressK3sContainerCustomizer withHelmValue(String key, Object value) {
        var copy = new HashMap<>(helmValues);
        copy.put(key, value);
        return withHelmValues(Collections.unmodifiableMap(copy));
    }

    @Override
    public void onRegister(K3sContainerCustomizers customizers) {
        customizers.configure(WaitStrategyCustomizer.class, wait -> wait.withAdditionalWaitStrategy(
                getClass(),
                Wait.forHttp("/")
                        .forPort(32080)
                        .forStatusCodeMatching((code) -> true)
                        .withStartupTimeout(Duration.ofMinutes(2))
        ));
    }

    @Override
    public void customize(K3sContainer container) {
        // List implementation from Arrays.asList does not support modifications
        var command = new ArrayList<>(Arrays.asList(container.getCommandParts()));
        command.remove("--disable=traefik");
        container.setCommandParts(command.toArray(String[]::new));

        // Configure traefik
        // exposing traefik on fixed port 80 on the host - traefik-config.yaml
        // ideally, we should get rid of the fixed port mapping - problems:
        // - keycloak auth url + keycloak redirect configuration
        container.addExposedPort(32080);
        container.withCreateContainerCmdModifier(createContainerCmd -> {
            createContainerCmd.getHostConfig().getPortBindings().bind(
                    new ExposedPort(32080),
                     Binding.bindPort(80)
             );
        });
        container.withCopyToContainer(
                Transferable.of(templateHelmChartConfig()),
                "/var/lib/rancher/k3s/server/manifests/traefik-config.yaml"
        );
    }

    @SneakyThrows(JsonProcessingException.class)
    private String templateHelmChartConfig() {
        // For configuration: see
        // - https://docs.k3s.io/helm#customizing-packaged-components-with-helmchartconfig
        // - https://github.com/traefik/traefik-helm-chart/blob/master/traefik/values.yaml
        return yamlMapper.writeValueAsString(Map.of(
                "apiVersion", "helm.cattle.io/v1",
                "kind", "HelmChartConfig",
                "metadata", Map.of(
                        "name", "traefik",
                        "namespace", "kube-system"
                ),
                "spec", Map.of(
                        "valuesContent", yamlMapper.writeValueAsString(convertToTree(helmValues))
                )
        ));
    }

    // package-private for testing
    static Map<String, Object> convertToTree(Map<String, Object> flat) {
        Map<String, Object> root = new HashMap<>();
        for (var entry : flat.entrySet()) {
            List<String> parts = splitPath(entry.getKey());
            Map<String, Object> targetMap = root;
            for (int i = 0; i < parts.size() - 1; i++) {
                if (targetMap.computeIfAbsent(parts.get(i), k -> new HashMap<>()) instanceof Map map) {
                    targetMap = map;
                } else {
                    throw new IllegalArgumentException("conflict at " + String.join(".", parts.subList(0, i + 1)));
                }
            }
            if(targetMap.put(parts.get(parts.size() - 1), entry.getValue()) instanceof Map) {
                throw new IllegalArgumentException("conflict at " + String.join(".", parts));
            }
        }
        return root;
    }

    private static List<String> splitPath(String key) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '\\' && i + 1 < key.length()) {
                i++; // skip escape character itself
                cur.append(key.charAt(i)); // next char is literal
            } else if (c == '.') {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts;
    }
}
