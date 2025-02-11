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


    public static void waitUntilDeploymentsReady(int timeout, List<String> deployments, KubernetesClient kubernetesClient) {
        // wait until expected deployments have available-replica
        await()
                .conditionEvaluationListener(new ConditionEvaluationLogger(log::info, SECONDS))
                .pollInterval(1, SECONDS)
                .atMost(timeout, SECONDS)
                .until(() -> deployments.stream()
                                .map(name -> deployment(name, kubernetesClient))
                                .filter(deployment -> deployment.availableReplicas() < 1)
                                .collect(Collectors.toSet()),
                        Matchers.empty()
                );
    }

    @NotNull
    private static NamedDeployment deployment(String name, KubernetesClient kubernetesClient) {
        return new NamedDeployment(name, kubernetesClient.apps().deployments().withName(name).get());
    }

    record NamedDeployment(String name, Deployment deployment) {

        boolean isNotFound() {
            return this.deployment == null;
        }

        int availableReplicas() {
            return this.isNotFound()
                    ? 0 : Objects.requireNonNullElse(this.deployment.getStatus().getAvailableReplicas(), 0);
        }

        int desiredReplicas() {
            return this.isNotFound()
                    ? -1 : Objects.requireNonNullElse(this.deployment.getStatus().getReplicas(), -1);
        }

        @Override
        public String toString() {
            return "%s (%s)".formatted(this.name,
                    this.isNotFound() ? "MISSING" : this.availableReplicas() + "/" + this.desiredReplicas());
        }
    }

}
