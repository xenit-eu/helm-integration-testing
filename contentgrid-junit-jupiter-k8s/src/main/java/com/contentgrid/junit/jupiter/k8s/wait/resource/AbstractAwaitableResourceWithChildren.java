package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import java.util.stream.Stream;
import lombok.NonNull;

public abstract class AbstractAwaitableResourceWithChildren<T extends HasMetadata, C extends HasMetadata> extends
        AbstractAwaitableResource<T> {

    AbstractAwaitableResourceWithChildren(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, T item) {
        super(client, factory, item);
    }

    @Override
    public Stream<Event> events() {
        return Stream.concat(
                super.events(),
                getRelatedResources()
                        .flatMap(AwaitableResource::events)
        );
    }

    @Override
    public Stream<LogLine> logs() {
        return getRelatedResources()
                .flatMap(AwaitableResource::logs);
    }

    protected Stream<AwaitableResource> getRelatedResources() {
        return createChildResourcesFilter()
                .list()
                .getItems()
                .stream()
                .filter(child -> child.hasOwnerReferenceFor(item))
                .map(child -> factory.instantiate(client, child));
    }

    protected abstract Listable<? extends KubernetesResourceList<? extends C>> createChildResourcesFilter();
}
