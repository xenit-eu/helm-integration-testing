package com.contentgrid.junit.jupiter.k8s;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.contentgrid.junit.jupiter.k8s.wait.KubernetesResourceWaiter;
import com.contentgrid.junit.jupiter.k8s.wait.ResourceMatcher;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionFactory;

/**
 * @deprecated Use {@link KubernetesResourceWaiter} to wait on configurable resources instead
 */
@Slf4j
@UtilityClass
@Deprecated(since = "0.1.0", forRemoval = true)
public class K8sTestUtils {

    private static UnaryOperator<ConditionFactory> createAwait(int timeout) {
        return await -> await.pollInterval(1, SECONDS).atMost(timeout, SECONDS);
    }

    public static void waitUntilDeploymentsReady(int timeout, List<String> deployments,
            KubernetesClient kubernetesClient, String namespace) {

        new KubernetesResourceWaiter(kubernetesClient)
                .deployments(ResourceMatcher.named(deployments.toArray(String[]::new)).inNamespace(namespace))
                .await(createAwait(timeout));
    }

    public static void waitUntilDeploymentsReady(int timeout, List<String> deployments,
            KubernetesClient kubernetesClient) {
        new KubernetesResourceWaiter(kubernetesClient)
                .deployments(ResourceMatcher.named(deployments.toArray(String[]::new)))
                .await(createAwait(timeout));
    }

    public static void waitUntilStatefulSetsReady(int timeout, List<String> statefulSets, KubernetesClient kubernetesClient) {
        new KubernetesResourceWaiter(kubernetesClient)
                .statefulSets(ResourceMatcher.named(statefulSets.toArray(String[]::new)))
                .await(createAwait(timeout));
    }

    public static void waitUntilStatefulSetsReady(int timeout, List<String> statefulSets,
            KubernetesClient kubernetesClient, String namespace) {
        new KubernetesResourceWaiter(kubernetesClient)
                .statefulSets(ResourceMatcher.named(statefulSets.toArray(String[]::new)).inNamespace(namespace))
                .await(createAwait(timeout));
    }
}
