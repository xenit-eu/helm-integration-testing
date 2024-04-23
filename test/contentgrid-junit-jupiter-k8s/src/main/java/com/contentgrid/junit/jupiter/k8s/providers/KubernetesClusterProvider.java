package com.contentgrid.junit.jupiter.k8s.providers;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
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

