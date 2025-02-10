package com.contentgrid.junit.jupiter.externalsecrets;

import static com.contentgrid.junit.jupiter.helpers.FieldHelper.findFields;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.insecure;
import static org.awaitility.Awaitility.await;

import com.contentgrid.junit.jupiter.helm.HasHelmClient;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import io.fabric8.junit.jupiter.HasKubernetesClient;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionEvaluationLogger;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

@Slf4j
public class ExternalSecretsExtension implements HasHelmClient, HasKubernetesClient, BeforeAllCallback,
        BeforeEachCallback, ParameterResolver {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var kubernetesClient = getClient(context);

        if (isCiliumNetworkPolicySupported(kubernetesClient)) {
            // Network policies to make sure external-secrets works
            kubernetesClient.load(
                            ExternalSecretsExtension.class.getResourceAsStream("/externalsecrets/networkpolicies.yaml"))
                    .serverSideApply();
        }

        var helm = getHelmClient(context);
        // setup external-secrets-operator
        helm.repository().add("external-secrets", "https://charts.external-secrets.io");
        helm.install().chart("external-secrets", "external-secrets/external-secrets");

        // wait until expected deployments have available-replica
        waitUntilDeploymentsReady(2 * 60,
                List.of("external-secrets-cert-controller", "external-secrets-webhook", "external-secrets"),
                kubernetesClient);

        // autowire static fields
        for (Field field : findFields(context, ClusterSecretStore.class, f -> Modifier.isStatic(f.getModifiers()))) {
            setFieldValue(field, null, new ClusterSecretStore(convertToValidName(field.getName()), kubernetesClient));
        }
        for (Field field : findFields(context, SecretStore.class, f -> Modifier.isStatic(f.getModifiers()))) {
            setFieldValue(field, null, new SecretStore(convertToValidName(field.getName()), kubernetesClient));
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var kubernetesClient = getClient(context);

        // autowire instance fields
        for (Field field : findFields(context, ClusterSecretStore.class, f -> !Modifier.isStatic(f.getModifiers()))) {
            setFieldValue(field, context.getRequiredTestInstance(), new ClusterSecretStore(convertToValidName(field.getName()), kubernetesClient));
        }
        for (Field field : findFields(context, SecretStore.class, f -> !Modifier.isStatic(f.getModifiers()))) {
            setFieldValue(field, context.getRequiredTestInstance(), new SecretStore(convertToValidName(field.getName()), kubernetesClient));
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        var type = parameterContext.getParameter().getType();
        return type.equals(ClusterSecretStore.class) || type.equals(SecretStore.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        var kubernetesClient = getClient(extensionContext);
        var type = parameterContext.getParameter().getType();
        var parameter = parameterContext.getParameter();
        var validName = convertToValidName(parameter.getDeclaringExecutable().getName()+"-"+parameter.getName());

        if (type.equals(ClusterSecretStore.class)) {
            return new ClusterSecretStore(validName, kubernetesClient);
        } else if (type.equals(SecretStore.class)) {
            return new SecretStore(validName, kubernetesClient);
        } else {
            throw new ParameterResolutionException("Unsupported parameter type: " + type);
        }
    }

    private static String convertToValidName(String input) {
        // Convert camelCase to camel-case
        String name = input.replaceAll("([a-z])([A-Z]+)", "$1-$2")
                .toLowerCase(); // Convert to lowercase and replace camelCase with camel-case
        name = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9')).or(CharMatcher.is('-'))
                .or(CharMatcher.is('.')).retainFrom(name); // Keep only valid chars
        name = name.replaceAll("[.-]{2,}", "-"); // Replace consecutive .- with -
        name = CharMatcher.anyOf("-.").trimFrom(name);  // Trim leading/trailing .-

        name = Strings.emptyToNull(name);
        if (name == null || !name.matches("^[a-z0-9].*[a-z0-9]$")) {
            return insecure().nextAlphabetic(10).toLowerCase(); // Return a random name if invalid
        }

        return name.substring(0, Math.min(253, name.length())); // Truncate if needed
    }

    private static boolean isCiliumNetworkPolicySupported(KubernetesClient client) {
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


    private void waitUntilDeploymentsReady(int timeout, List<String> deployments, KubernetesClient kubernetesClient) {
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
    private NamedDeployment deployment(String name, KubernetesClient kubernetesClient) {
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
