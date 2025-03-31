package com.contentgrid.junit.jupiter.k8s;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionEvaluationLogger;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;

@Slf4j
@UtilityClass
public class K8sTestUtils {

    public static boolean isCiliumNetworkPolicySupported(KubernetesClient client) {
        try {
            // Attempt to get the CRD
            CustomResourceDefinition crd = client.apiextensions().v1().customResourceDefinitions()
                    .withName("ciliumnetworkpolicies.cilium.io") // Name of the CiliumNetworkPolicy CRD
                    .get();

            // If we reach here without an exception, the CRD exists
            return crd != null;

        } catch (KubernetesClientException e) {
            // Catch the exception, which indicates the CRD doesn't exist
            if (e.getCode() == 404) { //check for the 404 code specifically
                return false;
            } else {
                // For other exceptions, it might be something else, rethrow or handle as needed
                throw e; // Or return false after logging the exception
            }
        }
    }

    public static void waitUntilDeploymentsReady(int timeout, List<String> deployments,
            KubernetesClient kubernetesClient, String namespace) {
        var client = namespace == null
                ? kubernetesClient.apps().deployments()
                : kubernetesClient.apps().deployments().inNamespace(namespace);
        // wait until expected deployments have available-replica
        await()
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info, SECONDS))
                .pollInterval(1, SECONDS)
                .atMost(timeout, SECONDS)
                .until(() -> deployments.stream()
                                .map(name -> client.withName(name).get())
                                .filter(deployment -> deployment.getStatus().getReplicas() -
                                        Objects.requireNonNullElse(deployment.getStatus().getReadyReplicas(), 0)
                                        > 0)
                                .collect(Collectors.toSet()),
                        Matchers.empty()
                );
    }

    public static void waitUntilDeploymentsReady(int timeout, List<String> deployments,
            KubernetesClient kubernetesClient) {
        waitUntilDeploymentsReady(timeout, deployments, kubernetesClient, null);
    }

    public static void waitUntilReplicaSetsReady(int timeout, List<String> replicaSets,
            KubernetesClient kubernetesClient) {
        // wait until expected replicaSets have available-replica
        await()
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info, SECONDS))
                .pollInterval(1, SECONDS)
                .atMost(timeout, SECONDS)
                .until(() -> replicaSets.stream()
                                .map(name -> kubernetesClient.apps().replicaSets().withName(name).get())
                                .filter(replicaset -> replicaset.getStatus().getReplicas() -
                                        Objects.requireNonNullElse(replicaset.getStatus().getReadyReplicas(), 0)
                                        > 0)
                                .collect(Collectors.toSet()),
                        Matchers.empty()
                );
    }

    public static void waitUntilStatefulSetsReady(int timeout, List<String> statefulSets, KubernetesClient kubernetesClient) {
        waitUntilStatefulSetsReady(timeout, statefulSets, kubernetesClient, null);
    }

    public static void waitUntilStatefulSetsReady(int timeout, List<String> statefulSets,
            KubernetesClient kubernetesClient, String namespace) {
        var client = namespace == null
                ? kubernetesClient.apps().statefulSets()
                : kubernetesClient.apps().statefulSets().inNamespace(namespace);
        await()
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info, SECONDS))
                .pollInterval(1, SECONDS)
                .atMost(timeout, SECONDS)
                .until(() -> statefulSets.stream()
                                .map(name -> client.withName(name).get())
                                .filter(statefulSet -> statefulSet.getStatus().getReplicas() -
                                        Objects.requireNonNullElse(statefulSet.getStatus().getReadyReplicas(), 0)
                                        > 0)
                                .collect(Collectors.toSet()),
                        Matchers.empty()
                );
    }

}
