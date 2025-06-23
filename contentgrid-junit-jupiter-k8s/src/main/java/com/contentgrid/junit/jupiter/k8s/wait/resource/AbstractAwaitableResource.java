package com.contentgrid.junit.jupiter.k8s.wait.resource;

import com.contentgrid.junit.jupiter.k8s.wait.resource.AwaitableResource.Event.RepeatCount;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractAwaitableResource<T extends HasMetadata> implements
        AwaitableResource {

    @NonNull
    protected final KubernetesClient client;
    @NonNull
    protected final AwaitableResourceFactory factory;
    @Getter
    protected final T item;

    @Override
    public Class<T> getApiType() {
        return (Class<T>) item.getClass();
    }

    @Override
    public ObjectReference getObjectReference() {
        return new ObjectReferenceBuilder()
                .withApiVersion(item.getApiVersion())
                .withKind(item.getKind())
                .withNamespace(item.getMetadata().getNamespace())
                .withName(item.getMetadata().getName())
                .withUid(item.getMetadata().getUid())
                .build();
    }

    @Override
    public Stream<Event> events() {
        return client.v1().events()
                .withInvolvedObject(getObjectReference())
                .resources()
                .map(Resource::require)
                .map(this::createEvent);
    }

    private Event createEvent(io.fabric8.kubernetes.api.model.Event event) {
        var firstTimestamp = Instant.parse(Objects.requireNonNullElseGet(event.getFirstTimestamp(), event.getMetadata()::getCreationTimestamp));
        var lastTimestamp = Instant.parse(Objects.requireNonNullElseGet(event.getLastTimestamp(), event.getMetadata()::getCreationTimestamp));
        return new Event(
                this,
                firstTimestamp,
                new RepeatCount(
                        Objects.requireNonNullElse(event.getCount(), 1),
                        Duration.between(firstTimestamp, lastTimestamp)
                ),
                event.getType(),
                event.getReason(),
                event.getMessage()
        );
    }

    @Override
    public String toString() {
        var objectReference = getObjectReference();
        return "%s %s/%s".formatted(objectReference.getKind(), objectReference.getNamespace(), objectReference.getName());
    }
}
