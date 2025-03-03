package com.contentgrid.junit.jupiter.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@KubernetesTestCluster
public class K8sTestUtilsTest {

    static KubernetesClient kubernetesClient;

    @Test
    void testWaitUntilReplicaSetsReady() {

        // Define labels for the ReplicaSet
        Map<String, String> labels = Map.of("app", "nginx");

        // Create the ReplicaSet
        ReplicaSet replicaSet = new ReplicaSetBuilder()
                .withNewMetadata()
                    .withName("nginx-replicaset")
                    .withNamespace("default")
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .withNewSelector()
                        .withMatchLabels(labels)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(labels)
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("nginx")
                                .withImage("nginx:1.21")
                                .addNewPort()
                                    .withContainerPort(80)
                                .endPort()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        // Deploy the ReplicaSet
        kubernetesClient.apps().replicaSets().resource(replicaSet).create();

        // Wait until the ReplicaSet is ready
        K8sTestUtils.waitUntilReplicaSetsReady(60, List.of("nginx-replicaset"), kubernetesClient);
        assertEquals(3, kubernetesClient.apps().replicaSets().withName("nginx-replicaset").get().getStatus().getReadyReplicas());
        System.out.println("ReplicaSet is ready");
    }

    @Test
    void testWaitUntilDeploymentsReady() {

        // Define labels for the Deployment
        Map<String, String> labels = Map.of("app", "nginx");

        // Create the Deployment
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                    .withName("nginx-deployment")
                    .withNamespace("default")
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(2)
                    .withNewSelector()
                        .withMatchLabels(labels)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(labels)
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("nginx")
                                .withImage("nginx:1.21")
                                .addNewPort()
                                    .withContainerPort(80)
                                .endPort()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        // Deploy the Deployment
        kubernetesClient.apps().deployments().resource(deployment).create();

        // Wait until the Deployment is ready
        K8sTestUtils.waitUntilDeploymentsReady(60, List.of("nginx-deployment"), kubernetesClient);
        assertEquals(2, kubernetesClient.apps().deployments().withName("nginx-deployment").get().getStatus().getReadyReplicas());
        System.out.println("Deployment is ready");
    }

}
