package com.contentgrid.testcontainers.k3s.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClusterDomainsK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void multipleDomains() {
        var kubernetesClient = createContainer(customizers -> {
            customizers.configure(ClusterDomainsK3sContainerCustomizer.class, dns -> dns.withDomains("cluster-domains.test", "example.net"));
        });

        assertScript(kubernetesClient, "nicolaka/netshoot", """
                set -ex
                [[ "$(dig a cluster-domains.test +short)" == "172.17.0.1" ]]
                [[ "$(dig a example.net +short)" == "172.17.0.1" ]]
        """);
    }

    @Test
    void noDomains() {
        var kubernetesClient = createContainer(customizers -> {
            customizers.configure(ClusterDomainsK3sContainerCustomizer.class);
        });

        assertThat(kubernetesClient.configMaps()
                .inNamespace("kube-system")
                .withName("coredns-custom")
                .get()).isNull();
    }

}