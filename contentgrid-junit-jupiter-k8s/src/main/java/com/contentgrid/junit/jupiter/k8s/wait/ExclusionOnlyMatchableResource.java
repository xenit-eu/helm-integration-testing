package com.contentgrid.junit.jupiter.k8s.wait;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

/**
 * A placeholder matchable resource that only collects exclusions.
 * <p>
 * It is can be upgraded to a {@link ResourceAccessorMatchableResource} when a resource accessor is added
 */
@RequiredArgsConstructor
class ExclusionOnlyMatchableResource<T extends HasMetadata> implements MatchableResource<T> {

    private final Class<T> type;
    private final Set<ResourceMatcher<? super T>> exclusions = new HashSet<>();

    @Override
    public void addMatcher(NamespacedResourceMatcher<? super T> matcher) {
        throw new UnsupportedOperationException(
                "Can not add matchers for resource %s that only has exclusions".formatted(type));
    }

    @Override
    public void addExclusion(ResourceMatcher<? super T> exclusion) {
        exclusions.add(exclusion);
    }

    @Override
    public CompletionStage<Void> start() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Stream<T> matchingResources() {
        return Stream.empty();
    }

    @Override
    public void close() {

    }

    @Override
    public MatchableResource<T> tryUpgrade(
            MixedOperation<T, ? extends KubernetesResourceList<T>, ? extends Resource<T>> resourceAccessor) {
        if (resourceAccessor != null) {
            var upgraded = new ResourceAccessorMatchableResource<>(resourceAccessor);
            exclusions.forEach(upgraded::addExclusion);
            return upgraded;
        }
        return this;
    }
}
