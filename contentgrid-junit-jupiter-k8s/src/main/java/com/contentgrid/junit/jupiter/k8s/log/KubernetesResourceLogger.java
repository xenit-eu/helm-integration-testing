package com.contentgrid.junit.jupiter.k8s.log;

import com.contentgrid.junit.jupiter.k8s.resource.AwaitableResource;
import com.contentgrid.junit.jupiter.k8s.resource.AwaitableResource.LogLine;
import com.contentgrid.junit.jupiter.k8s.resource.ConfigurableResourceSet;
import com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher;
import com.contentgrid.junit.jupiter.k8s.resource.ResourceMatchingSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Instant;
import java.util.stream.Stream;
import lombok.NonNull;

public class KubernetesResourceLogger implements ResourceMatchingSpec<KubernetesResourceLogger>, AutoCloseable {
    private final ConfigurableResourceSet resourceSet;

    public KubernetesResourceLogger(KubernetesClient client) {
        this.resourceSet = ConfigurableResourceSet.of(client);
    }

    @Override
    public <T extends HasMetadata> KubernetesResourceLogger include(@NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher) {
        resourceSet.include(clazz, matcher);
        return this;
    }

    @Override
    public <T extends HasMetadata> KubernetesResourceLogger exclude(@NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher) {
        resourceSet.exclude(clazz, matcher);
        return this;
    }


    public Stream<LogLine> logsSince(@NonNull Instant since) {
        return resourceSet.stream()
                .flatMap(AwaitableResource::logs)
                .filter(logLine -> since.isAfter(logLine.timestamp()));
    }

    @Override
    public void close() {
        resourceSet.close();
    }
}
