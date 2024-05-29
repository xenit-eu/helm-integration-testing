package com.contentgrid.junit.jupiter.k8s;

import static com.contentgrid.junit.jupiter.docker.registry.DockerRegistryCacheExtension.DOCKERMIRROR_NAMESPACE;

import com.contentgrid.junit.jupiter.docker.registry.DockerRegistryEndpoint;
import com.contentgrid.junit.jupiter.k8s.providers.KubernetesClusterProvider;
import com.contentgrid.junit.jupiter.k8s.providers.KubernetesClusterProvider.KubernetesProviderResult;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.StringUtils;

@Slf4j
public class KubernetesTestClusterExtension implements BeforeAllCallback, AfterAllCallback, ExecutionCondition {

    private static final Namespace NAMESPACE = Namespace.create(KubernetesTestClusterExtension.class);

    private static final String SYSPROP_KUBECONFIG = "kubeconfig";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var store = context.getStore(NAMESPACE);

        var annotation = this.findAnnotation(context)
                .orElseThrow(() -> {
                    var message = "@%s not found".formatted(KubernetesTestCluster.class.getSimpleName());
                    return new ExtensionConfigurationException(message);
                });

        var kubeconfig = System.getProperty(SYSPROP_KUBECONFIG);
        if (!StringUtils.isBlank(kubeconfig)) {
            // store away existing 'kubeconfig' system property
            log.warn("System property '{}' with value '{}' is ignored", SYSPROP_KUBECONFIG, kubeconfig);
            store.put("restore_kubeconfig", kubeconfig);
        }

        var result = store.getOrComputeIfAbsent(KubernetesClusterProvider.class,
                key -> Arrays.stream(annotation.providers())
                        .map(ReflectionSupport::newInstance)
                        .filter(candidate -> candidate.evaluate().isEnabled())
                        .findFirst()
                        .map(provider -> configureRegistryMirrors(provider, context))
                        .map(KubernetesClusterProvider::start)
                        .orElseThrow(() -> new ExtensionConfigurationException(
                                "No suitable %s found".formatted(KubernetesClusterProvider.class.getSimpleName()))),
                KubernetesProviderResult.class);

        // write kubeconfig contents to temp .yml file
        log.debug("kubeconfig: {}", result.getKubeConfigYaml());
        var kubeConfigPath = Files.createTempFile("kubeconfig-", ".yml");
        Files.writeString(kubeConfigPath, result.getKubeConfigYaml());

        store.put("kubeconfig", kubeConfigPath.toString());
        System.setProperty(SYSPROP_KUBECONFIG, kubeConfigPath.toString());
    }

    KubernetesClusterProvider configureRegistryMirrors(KubernetesClusterProvider provider, ExtensionContext context) {
        var store = context.getStore(DOCKERMIRROR_NAMESPACE);
        var mirrors = store.get("mirrors", Set.class);

        if (mirrors == null) {
            return provider;
        }

        for(var mirror : mirrors) {
            var name = mirror.toString();
            var endpoint = store.get(name, DockerRegistryEndpoint.class);
            log.info("configuring docker registry mirror for '{}' -> {}", endpoint.getName(), endpoint.getURI());
            provider.addDockerRegistryMirror(endpoint.getName(), endpoint.getURI().toString());
        }

        return provider;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return this.findAnnotation(context)
                .map(this::evaluate)
                .orElseThrow(() -> new ExtensionConfigurationException("@KubernetesTestCluster not found"));
    }

    private ConditionEvaluationResult evaluate(KubernetesTestCluster annotation) {
        var providers = List.of(annotation.providers());

        if (providers.isEmpty()) {
            return ConditionEvaluationResult.disabled("providers is empty");
        }

        // check if any of the configured providers is enabled
        return Arrays.stream(annotation.providers())
                .map(ReflectionSupport::newInstance)
                .filter(candidate -> candidate.evaluate().isEnabled())
                .findFirst()
                .map(provider -> ConditionEvaluationResult.enabled("available provider: %s".formatted(provider)))
                .orElseGet(() -> ConditionEvaluationResult.disabled("configured provider(s) not available"));
    }


    private Optional<KubernetesTestCluster> findAnnotation(ExtensionContext context) {
        Optional<ExtensionContext> current = Optional.of(context);
        while (current.isPresent()) {
            Optional<KubernetesTestCluster> annotation = AnnotationSupport.findAnnotation(
                    current.get().getRequiredTestClass(),
                    KubernetesTestCluster.class
            );
            if (annotation.isPresent()) {
                return annotation;
            }
            current = current.get().getParent();
        }
        return Optional.empty();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var store = context.getStore(NAMESPACE);

        var restoreKubeconfig = store.get("restore_kubeconfig", String.class);
        if (StringUtils.isBlank(restoreKubeconfig)) {
            System.clearProperty(SYSPROP_KUBECONFIG);
        } else {
            log.warn("Restoring system property {}={}", SYSPROP_KUBECONFIG, restoreKubeconfig);
            System.setProperty(SYSPROP_KUBECONFIG, restoreKubeconfig);
        }
    }
}
