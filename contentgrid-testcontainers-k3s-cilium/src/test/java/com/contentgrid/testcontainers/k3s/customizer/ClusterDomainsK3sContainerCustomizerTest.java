package com.contentgrid.testcontainers.k3s.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.testcontainers.k3s.customizer.ClusterDomainsK3sContainerCustomizer.ResolutionTarget;
import org.junit.jupiter.api.Test;

class ClusterDomainsK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void bridgeIp() {
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

    @Test
    void nodeIp() {
        var container = createContainerOnly(customizers -> {
            customizers.configure(ClusterDomainsK3sContainerCustomizer.class, dns -> dns.withDomains("cluster-domains.test", "example.net").withResolution(
                    ResolutionTarget.INTERNAL_NODE_IP));
        });

        var client = createClientFromContainer(container);

        assertScript(client, "nicolaka/netshoot", """
                set -ex
                node_ip="$(dig a %s +short)"
                [[ "$(dig a cluster-domains.test +short)" == "$node_ip" ]]
                [[ "$(dig a example.net +short)" == "$node_ip" ]]
                """.formatted(container.getContainerInfo().getConfig().getHostName()));

    }

}