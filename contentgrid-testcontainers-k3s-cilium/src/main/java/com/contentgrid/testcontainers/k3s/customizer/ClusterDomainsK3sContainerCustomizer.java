package com.contentgrid.testcontainers.k3s.customizer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.With;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures domain names that will resolve to the docker host bridge IP itself
 * <p>
 * Together with host port mapping and an ingress controller, this can be used to resolve test domains to the cluster itself
 * <p>
 * This configuration is only applicable <em>inside</em> the cluster itself, it does not affect DNS resolution on the host
 */
@AllArgsConstructor
@EqualsAndHashCode
@With
public class ClusterDomainsK3sContainerCustomizer implements K3sContainerCustomizer {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR)
    );

    private final Set<String> domains;

    /**
     * Configures what the {@link #domains} will resolve to
     */
    private final ResolutionTarget resolution;

    public enum ResolutionTarget {
        /**
         * Resolve domains to the docker host's bridge IP (172.17.0.1).
         * This allows access to all ports that are exposed on the <em>host</em> itself.
         * To access cluster services this way requires a <code>NodePort</code> <code>Service</code> in kubernetes and a port binding for the docker container
         */
        HOST_BRIDGE_IP,
        /**
         * Resolve domains to the internal node IP.
         * This allows access to all ports that are exposed in the <em>kubernetes node</em>.
         * To access cluster services this way requires a <code>NodePort</code> or <code>LoadBalancer</code> <code>Service</code> in kubernetes
         */
        INTERNAL_NODE_IP
    }

    public ClusterDomainsK3sContainerCustomizer() {
        this(Set.of(), ResolutionTarget.HOST_BRIDGE_IP);
    }

    public ClusterDomainsK3sContainerCustomizer withDomains(Collection<String> domains) {
        return new ClusterDomainsK3sContainerCustomizer(Set.copyOf(domains), resolution);

    }

    public ClusterDomainsK3sContainerCustomizer withDomains(String... domains) {
        return new ClusterDomainsK3sContainerCustomizer(Set.of(domains), resolution);
    }

    @Override
    public void customize(K3sContainer container) {
        if(domains.isEmpty()) {
            return;
        }

        var containerHost = UUID.randomUUID().toString().replace("-", "");
        container.withCopyToContainer(
                Transferable.of(createCorednsConfig(containerHost)),
                "/var/lib/rancher/k3s/server/manifests/coredns-config.yaml"
        );
        if (resolution == ResolutionTarget.INTERNAL_NODE_IP) {
            container.withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName(containerHost);
            });
        }
    }

    @SneakyThrows(JsonProcessingException.class)
    private String createCorednsConfig(String containerHost) {
        var configData = switch (resolution) {
            case HOST_BRIDGE_IP -> domains.stream().collect(Collectors.toMap(domain -> domain+".server", this::createDomainConfig));
            case INTERNAL_NODE_IP -> Map.of(
                    "cluster-domains-rewrite.override", domains.stream().map(domain -> this.createRewriteConfig(domain, containerHost)).collect(Collectors.joining("\n"))
            );
        };

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

    private String createRewriteConfig(String domain, String containerHost) {
        return "rewrite name %s %s".formatted(domain, containerHost);
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
