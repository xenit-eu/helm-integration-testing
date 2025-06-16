package com.contentgrid.junit.jupiter.k8s.wait;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Internal interface for handling configuration of resource matching
 * @param <T> The type of the kubernetes resource
 */
interface MatchableResource<T extends HasMetadata> extends AutoCloseable {

    /**
     * Register an additional matcher
     */
    void addMatcher(NamespacedResourceMatcher<? super T> matcher);

    /**
     * Add an exclusion
     */
    void addExclusion(ResourceMatcher<? super T> exclusion);

    /**
     * Starts the informers
     */
    CompletionStage<Void> start();

    /**
     * @return A stream of all matching resources
     */
    Stream<T> matchingResources();

    /**
     * Shuts down all informers
     */
    @Override
    void close();

}
