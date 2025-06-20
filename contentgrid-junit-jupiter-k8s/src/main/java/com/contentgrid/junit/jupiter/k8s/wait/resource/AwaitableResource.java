package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectReference;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import lombok.NonNull;

public interface AwaitableResource {
    Class<? extends KubernetesResource> getApiType();

    ObjectReference getObjectReference();

    boolean isReady();

    Stream<LogLine> logs();
    Stream<Event> events();

    record LogLine(
            @NonNull
            AwaitableResource resource,
            @NonNull
            Instant timestamp,
            @NonNull
            String container,
            @NonNull
            String line
    ) {

    }

    record Event(
            @NonNull
            AwaitableResource resource,
            @NonNull
            Instant timestamp,
            @NonNull
            RepeatCount repeat,
            @NonNull
            String type,
            @NonNull
            String reason,
            @NonNull
            String message
    ) {
        public record RepeatCount(
                int count,
                @NonNull
                Duration period
        ) {

        }

    }

}
