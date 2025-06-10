package com.contentgrid.junit.jupiter.k8s;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionEvaluationLogger;
import org.hamcrest.Matchers;

@Slf4j
@UtilityClass
public class K8sTestUtils {

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
                                .filter(deployment -> {
                                    if (deployment == null || deployment.getStatus() == null || deployment.getStatus().getReplicas() == null) {
                                        return true;
                                    }
                                    return deployment.getStatus().getReplicas() -
                                            Objects.requireNonNullElse(deployment.getStatus().getReadyReplicas(), 0)
                                            > 0;
                                })
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
        waitUntilReplicaSetsReady(timeout, replicaSets, kubernetesClient, null);
    }

    public static void waitUntilReplicaSetsReady(int timeout, List<String> replicaSets,
            KubernetesClient kubernetesClient, String namespace) {
        var client = namespace == null
                ? kubernetesClient.apps().replicaSets()
                : kubernetesClient.apps().replicaSets().inNamespace(namespace);

        // wait until expected replicaSets have available-replica
        await()
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info, SECONDS))
                .pollInterval(1, SECONDS)
                .atMost(timeout, SECONDS)
                .until(() -> replicaSets.stream()
                                .map(name -> client.withName(name).get())
                                .filter(replicaset -> {
                                    if (replicaset == null || replicaset.getStatus() == null || replicaset.getStatus().getReplicas() == null) {
                                        return true;
                                    }
                                    return replicaset.getStatus().getReplicas() -
                                            Objects.requireNonNullElse(replicaset.getStatus().getReadyReplicas(), 0)
                                            > 0;
                                })
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
                                .filter(statefulSet -> {
                                    if (statefulSet == null || statefulSet.getStatus() == null || statefulSet.getStatus().getReplicas() == null) {
                                        return true;
                                    }
                                    return statefulSet.getStatus().getReplicas() -
                                            Objects.requireNonNullElse(statefulSet.getStatus().getReadyReplicas(), 0)
                                            > 0;
                                })
                                .collect(Collectors.toSet()),
                        Matchers.empty()
                );
    }

    // TODO We might want to simplify these very-similar looking methods at some point, but I couldn't get past making
    //   a generic <T extends HasMetadata> method where I run into issues on .filter(resource -> resource.getStatus())
    //   because Fabric8 doesn't have an interface for "has status", and besides, the status on a StatefulSet is a
    //   different type than on a Deployment.

}
