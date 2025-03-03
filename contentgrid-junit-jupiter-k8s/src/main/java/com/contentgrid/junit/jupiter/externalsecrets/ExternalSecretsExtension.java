package com.contentgrid.junit.jupiter.externalsecrets;

import static com.contentgrid.junit.jupiter.helpers.FieldHelper.findFields;
import static com.contentgrid.junit.jupiter.k8s.K8sTestUtils.isCiliumNetworkPolicySupported;
import static com.contentgrid.junit.jupiter.k8s.K8sTestUtils.waitUntilDeploymentsReady;
import static org.apache.commons.lang3.RandomStringUtils.insecure;

import com.contentgrid.junit.jupiter.helm.HasHelmClient;
import io.fabric8.junit.jupiter.HasKubernetesClient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension for setting up External Secrets Operator.
 * <p>
 * This extension installs the External Secrets Operator and sets up the necessary network policies.
 * <p>
 * It also provides autowiring for {@link ClusterSecretStore} and {@link SecretStore} fields.
 * <p>
 * To use this extension, annotate your test class with {@link FakeSecretStore} and inject {@link ClusterSecretStore}
 * and {@link SecretStore} fields.
 */
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
        // Convert camelCase to kebab-case
        String name = input.replaceAll("([a-z])([A-Z]+)", "$1-$2")
                .toLowerCase();

        // Keep only valid chars (a-z, 0-9, dash, dot)
        name = name.replaceAll("[^a-z0-9.-]", "");

        // Replace consecutive dots or dashes with a single dash
        name = name.replaceAll("[.-]{2,}", "-");

        // Trim leading/trailing dots and dashes
        name = name.replaceAll("^[.-]+|[.-]+$", "");

        // Handle empty or invalid names
        if (name.isEmpty() || !name.matches("^[a-z0-9].*[a-z0-9]$")) {
            return insecure().nextAlphabetic(10).toLowerCase();
        }

        // Truncate if needed
        return name.substring(0, Math.min(253, name.length()));
    }
}
