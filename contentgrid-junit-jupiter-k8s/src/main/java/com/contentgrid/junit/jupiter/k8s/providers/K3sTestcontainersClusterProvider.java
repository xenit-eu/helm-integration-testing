package com.contentgrid.junit.jupiter.k8s.providers;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import com.contentgrid.testcontainers.k3s.customizer.FreezableK3sContainerCustomizersImpl;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a kubernetes cluster by using a K3S docker container, optionally customized with {@link K3sContainerCustomizer}s
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class K3sTestcontainersClusterProvider implements KubernetesClusterProvider,
        K3sContainerCustomizers {

    public static final DockerImageName IMAGE_RANCHER_K3S = DockerImageName.parse("rancher/k3s");

    @NonNull
    protected final K3sContainer container;

    @NonNull
    @Delegate(types = K3sContainerCustomizers.class)
    private final FreezableK3sContainerCustomizersImpl customizers = new FreezableK3sContainerCustomizersImpl();

    public K3sTestcontainersClusterProvider() {
        this(new K3sContainer(IMAGE_RANCHER_K3S));
    }

    @Override
    public ProviderEvaluationResult evaluate() {
        if (!this.isDockerAvailable()) {
            return ProviderEvaluationResult.disabled("docker is not available");
        }

        return ProviderEvaluationResult.enabled();
    }

    @Override
    public KubernetesProviderResult start() {
        log.info("Starting k3s: {}", String.join(" ", this.container.getCommandParts()));

        this.customizers.customize(this.container);
        this.customizers.freeze();

        this.container.start();

        return new DelegatedKubernetesProviderResult(this.container.getKubeConfigYaml(), this::stop);
    }

    @Override
    public void stop() {
        log.debug("Stopping {}", this.container);
        this.container.close();
    }

    boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    @Override
    public void addDockerRegistryMirror(String name, String endpoint) {
        configure(RegistryMirrorsK3sContainerCustomizer.class, mirrors -> mirrors.withMirror(name, endpoint));
    }
}
