package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A set of kubernetes resources, based on {@link ResourceMatcher}.
 */
@RequiredArgsConstructor
public class ResourceSet implements AutoCloseable {
    private final KubernetesClient client;

    private final Map<Class<HasMetadata>, MatchableResource<HasMetadata>> matchableResources = new HashMap<>();

    private static final Map<Class<? extends HasMetadata>, Function<KubernetesClient, MixedOperation<? extends HasMetadata, ? extends KubernetesResourceList<? extends HasMetadata>, ? extends Resource<? extends HasMetadata>>>> RESOURCE_ACCESSORS = Map.of(
            Deployment.class, client -> client.apps().deployments(),
            StatefulSet.class, client -> client.apps().statefulSets(),
            DaemonSet.class, client -> client.apps().daemonSets(),
            Job.class, client -> client.batch().v1().jobs(),
            ReplicaSet.class, client -> client.apps().replicaSets()
    );

    private static final AwaitableResourceFactory awaitableResourceFactory = new AwaitableResourceFactory();

    static {
        awaitableResourceFactory.registerFactory(Deployment.class, DeploymentAwaitableResource::new);
        awaitableResourceFactory.registerFactory(ReplicaSet.class, ReplicaSetAwaitableResource::new);
        awaitableResourceFactory.registerFactory(Pod.class, PodAwaitableResource::new);
        awaitableResourceFactory.registerFactory(Job.class, JobAwaitableResource::new);
        awaitableResourceFactory.registerFactory(StatefulSet.class, StatefulSetAwaitableResource::new);
        awaitableResourceFactory.registerFactory(DaemonSet.class, DaemonSetAwaitableResource::new);
    }

    private <T extends HasMetadata> MatchableResource<T> matchableResource(
            @NonNull Class<T> type
    ) {
        return (MatchableResource<T>) matchableResources.computeIfAbsent((Class<HasMetadata>)type, (t) -> new MatchableResource<>(
                (MixedOperation<HasMetadata, ? extends KubernetesResourceList<HasMetadata>, ? extends Resource<HasMetadata>>) RESOURCE_ACCESSORS.get(t).apply(client)
        ));
    }

    private <T extends HasMetadata> ResourceMatcher.NamespacedResourceMatcher<T> makeNamespacedResourceMatcher(
            ResourceMatcher<T> matcher) {
        if(matcher instanceof ResourceMatcher.NamespacedResourceMatcher<T> namespacedResourceMatcher) {
            return namespacedResourceMatcher;
        }
        return new NamespacedResourceMatcherImpl<>(matcher, client.getNamespace());
    }

    /**
     * Include certain resources to the wait
     * @param clazz Resource type to include
     * @param matcher Matcher for the resource type
     */
    public <T extends HasMetadata> ResourceSet include(
            @NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher
    ) {
        matchableResource(clazz).addMatcher(makeNamespacedResourceMatcher(matcher));
        return this;
    }


    /**
     * Exclude certain resources from the wait
     * @param clazz Resource type to exclude
     * @param matcher Matcher for the resource type
     */
    public <T extends HasMetadata> ResourceSet exclude(
            @NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher
    ) {
        matchableResource(clazz).addExclusion(matcher);
        return this;
    }

    /**
     * @return Stream of all matching that will be waited on
     */
    public Stream<? extends AwaitableResource> stream() {
        // Start all informers in parallel and wait for them to be running
        CompletableFuture.allOf(matchableResources.values().stream()
                        .map(MatchableResource::start)
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new))
                .join();

        return matchableResources.values()
                .stream()
                .flatMap(MatchableResource::matchingResources)
                .map(resource -> awaitableResourceFactory.instantiate(client, resource));
    }

    @Override
    public void close() {

        for (var matchableResource : matchableResources.values()) {
            matchableResource.close();
        }
    }
}
