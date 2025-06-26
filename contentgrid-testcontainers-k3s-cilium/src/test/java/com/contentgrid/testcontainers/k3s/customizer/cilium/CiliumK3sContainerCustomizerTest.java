package com.contentgrid.testcontainers.k3s.customizer.cilium;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.testcontainers.k3s.customizer.AbstractK3sContainerCustomizerTest;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import org.junit.jupiter.api.Test;

class CiliumK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void ciliumSetup() {
        var client = createContainer(customizers -> {
            customizers.configure(CiliumK3sContainerCustomizer.class);
        });

        // Check that we have cilium CRDs
        assertThat(client.apiextensions().v1().customResourceDefinitions()
                .withName("ciliumnetworkpolicies.cilium.io")
                .get()
        ).isNotNull();

        // We should still have access to the kubernetes API, because there is no default-deny policy installed
        assertScript(client, "bitnami/kubectl", """
                set -ex
                kubectl api-resources
                """);

        var networkPolicy = client.resource("""
                apiVersion: cilium.io/v2
                kind: CiliumClusterwideNetworkPolicy
                metadata:
                    name: block-test
                spec:
                    endpointSelector:
                        matchLabels:
                            k8s:test: blocked-egress
                    egress:
                        - {}
                """)
                .create();

        // Here, we shouldn't have access to the kubernetes API, because the egress is blocked for the pod
        assertScript(client, "bitnami/kubectl", """
                set -ex
                if kubectl api-resources; then
                    exit 1
                else
                    exit 0
                fi
                """, job -> {
            return new JobBuilder(job)
                    .editSpec()
                    .editTemplate()
                    .editMetadata()
                    .addToLabels("test", "blocked-egress")
                    .endMetadata()
                    .endTemplate()
                    .endSpec()
                    .build();
        });

        client.resource(networkPolicy).delete();

    }

}