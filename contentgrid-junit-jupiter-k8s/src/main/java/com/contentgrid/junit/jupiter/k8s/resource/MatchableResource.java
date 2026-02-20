package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Internal class for handling configuration of resource matching
 * @param <T> The type of the kubernetes resource
 */
@RequiredArgsConstructor
class MatchableResource<T extends HasMetadata> implements AutoCloseable {

    @NonNull
    private final MixedOperation<T, ? extends KubernetesResourceList<T>, ? extends Resource<T>> resourceAccessor;
    private final Set<ResourceMatcher<? super T>> matchers = new HashSet<>();
    private final Set<ResourceMatcher<? super T>> exclusions = new HashSet<>();
    private final Map<String, SharedIndexInformer<T>> informers = new HashMap<>();

    /**
     * Register an additional matcher
     */
    public void addMatcher(ResourceMatcher.NamespacedResourceMatcher<? super T> matcher) {
        matchers.add(matcher);
        informers.computeIfAbsent(matcher.getNamespace(),
                ns -> resourceAccessor.inNamespace(ns).runnableInformer(0));
    }

    /**
     * Add an exclusion
     */
    public void addExclusion(ResourceMatcher<? super T> exclusion) {
        matchers.remove(exclusion);
        exclusions.add(exclusion);
    }

    /**
     * Starts the informers
     */
    public CompletionStage<Void> start() {
        return CompletableFuture.allOf(
                informers.values()
                        .stream()
                        .map(SharedIndexInformer::start)
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new)
        );
    }

    /**
     * @return A stream of all matching resources
     */
    public Stream<T> matchingResources() {
        return informers.values()
                .stream()
                .flatMap(informer -> informer.getStore().list().stream())
                .filter(item -> exclusions.stream().noneMatch(matcher -> matcher.test(item)))
                .filter(item -> matchers.stream().anyMatch(matcher -> matcher.test(item)));
    }

    /**
     * Shuts down all informers
     */
    public void close() {
        for (var informer : informers.values()) {
            informer.close();
        }
    }
}
