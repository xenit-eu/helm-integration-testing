package com.contentgrid.testcontainers.k3s.customizer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures domain names that will resolve to the docker host bridge IP itself
 * <p>
 * Together with host port mapping and an ingress controller, this can be used to resolve test domains to the cluster itself
 * <p>
 * This configuration is only applicable <em>inside</em> the cluster itself, it does not affect DNS resolution on the host
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public class ClusterDomainsK3sContainerCustomizer implements K3sContainerCustomizer {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR)
    );
    private final Set<String> domains;

    public ClusterDomainsK3sContainerCustomizer() {
        this(Set.of());
    }

    public ClusterDomainsK3sContainerCustomizer withDomains(Collection<String> domains) {
        return new ClusterDomainsK3sContainerCustomizer(Set.copyOf(domains));

    }

    public ClusterDomainsK3sContainerCustomizer withDomains(String... domains) {
        return new ClusterDomainsK3sContainerCustomizer(Set.of(domains));
    }

    @Override
    public void customize(K3sContainer container) {
        if(domains.isEmpty()) {
            return;
        }
        container.withCopyToContainer(
                Transferable.of(createCorednsConfig()),
                "/var/lib/rancher/k3s/server/manifests/coredns-config.yaml"
        );
    }

    @SneakyThrows(JsonProcessingException.class)
    private String createCorednsConfig() {
        var configData = domains.stream().collect(Collectors.toMap(domain -> domain+".server", this::createDomainConfig));

        var config = Map.of(
                "apiVersion", "v1",
                "kind", "ConfigMap",
                "metadata", Map.of(
                        "name", "coredns-custom",
                        "namespace", "kube-system"
                ),
                "data", configData
        );

        return yamlMapper.writeValueAsString(config);
    }

    private String createDomainConfig(String domain) {
        return """
                %1$s:53 {
                    hosts {
                        172.17.0.1 %1$s
                    }
                }
                """.formatted(domain);
    }
}
