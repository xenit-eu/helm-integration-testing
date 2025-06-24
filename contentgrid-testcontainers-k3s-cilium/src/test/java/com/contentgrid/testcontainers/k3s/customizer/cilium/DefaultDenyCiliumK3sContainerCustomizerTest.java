package com.contentgrid.testcontainers.k3s.customizer.cilium;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.testcontainers.k3s.customizer.AbstractK3sContainerCustomizerTest;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import org.junit.jupiter.api.Test;

class DefaultDenyCiliumK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void ciliumSetup() {
        var client = createContainer(customizers -> {
            customizers.configure(DefaultDenyCiliumK3sContainerCustomizer.class);
        });

        // Check that we have cilium CRDs
        assertThat(client.apiextensions().v1().customResourceDefinitions()
                .withName("ciliumnetworkpolicies.cilium.io")
                .get()
        ).isNotNull();

        // We should not have access to the kubernetes API, because there is a default-deny policy installed
        assertScript(client, "bitnami/kubectl", """
                set -ex
                if kubectl api-resources; then
                    exit 1
                else
                    exit 0
                fi
                """);

        var networkPolicy = client.resource("""
                apiVersion: cilium.io/v2
                kind: CiliumClusterwideNetworkPolicy
                metadata:
                    name: allow-test
                spec:
                    endpointSelector:
                        matchLabels:
                            k8s:test: allowed-egress
                    egress:
                        - toEntities:
                            - kube-apiserver
                """)
                .create();

        // Here, we should have access to the kubernetes API, because the egress is allowed for the pod
        assertScript(client, "bitnami/kubectl", """
                set -ex
                kubectl api-resources
                """, job -> {
            return new JobBuilder(job)
                    .editSpec()
                    .editTemplate()
                    .editMetadata()
                    .addToLabels("test", "allowed-egress")
                    .endMetadata()
                    .endTemplate()
                    .endSpec()
                    .build();
        });

        client.resource(networkPolicy).delete();

    }

}