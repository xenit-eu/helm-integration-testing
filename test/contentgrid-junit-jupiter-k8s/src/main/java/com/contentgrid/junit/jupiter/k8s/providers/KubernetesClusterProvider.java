package com.contentgrid.junit.jupiter.k8s.providers;

import com.contentgrid.junit.jupiter.k8s.providers.KubernetesClusterProvider.KubernetesProviderResult;
import java.io.Closeable;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

public interface KubernetesClusterProvider extends AutoCloseable {

    ProviderEvaluationResult evaluate();

    KubernetesProviderResult start();

    void stop();

    @Override
    default void close() {
        this.stop();
    }

    default void addDockerRegistryMirror(String name, String endpoint) {

    }


    interface KubernetesProviderResult extends CloseableResource {

        String getKubeConfigYaml();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class ProviderEvaluationResult {

        @Getter
        private final boolean enabled;

        private final String reason;

        public static ProviderEvaluationResult enabled() {
            return new ProviderEvaluationResult(true, null);
        }

        public static ProviderEvaluationResult disabled(String reason) {
            return new ProviderEvaluationResult(false, reason);
        }

        public Optional<String> getReason() {
            return Optional.ofNullable(reason);
        }
    }
}
@RequiredArgsConstructor
class DelegatedKubernetesProviderResult implements KubernetesProviderResult {

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
