package com.contentgrid.testcontainers.k3s.customizer;

import java.util.Collection;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
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

    private String createCorednsConfig() {
        var template =
                """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: coredns-custom
                  namespace: kube-system
                data:
                  helm-integration-testing.test.server: |
                    helm-integration-testing.test:53 {
                        errors
                        hosts {
                            172.17.0.1 %s
                            fallthrough
                        }
                        forward . 127.0.0.1
                    }
                """;
        var domainString = String.join(" ", domains);
        return template.formatted(domainString);
    }
}
