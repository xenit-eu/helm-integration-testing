package com.contentgrid.junit.jupiter.k8s.providers;

import java.io.Closeable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class K3sTestcontainersClusterProvider implements KubernetesClusterProvider {

    public static final DockerImageName IMAGE_RANCHER_K3S = DockerImageName.parse("rancher/k3s");

    @NonNull
    protected final K3sContainer container;

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
        this.container.start();
        return new K3sProviderResult(this.container.getKubeConfigYaml(), this::stop);
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

    @RequiredArgsConstructor
    static class K3sProviderResult implements KubernetesProviderResult {

        @Getter
        @NonNull
        final String kubeConfigYaml;

        @NonNull
        final Closeable closeableDelegate;

        @Override
        public void close() throws Throwable {
            this.closeableDelegate.close();
        }
    }
}
