package com.contentgrid.junit.jupiter.k8s.wait;

import com.contentgrid.junit.jupiter.k8s.wait.resource.AwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.AwaitableResourceFactory;
import com.contentgrid.junit.jupiter.k8s.wait.resource.DaemonSetAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.DeploymentAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.JobAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.PodAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.ReplicaSetAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.StatefulSetAwaitableResource;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionEvaluationListener;
import org.awaitility.core.ConditionEvaluationLogger;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.EvaluatedCondition;
import org.awaitility.core.IgnoredException;
import org.awaitility.core.StartEvaluationEvent;
import org.awaitility.core.TimeoutEvent;
import org.hamcrest.Matchers;

/**
 * Utility to wait for kubernetes resources to become ready
 */
@RequiredArgsConstructor
@Slf4j
public class KubernetesResourceWaiter implements AutoCloseable {
    private final KubernetesClient client;

    private static final AwaitableResourceFactory awaitableResourceFactory = new AwaitableResourceFactory();

    static {
        awaitableResourceFactory.registerFactory(Deployment.class, DeploymentAwaitableResource::new);
        awaitableResourceFactory.registerFactory(ReplicaSet.class, ReplicaSetAwaitableResource::new);
        awaitableResourceFactory.registerFactory(Pod.class, PodAwaitableResource::new);
        awaitableResourceFactory.registerFactory(Job.class, JobAwaitableResource::new);
        awaitableResourceFactory.registerFactory(StatefulSet.class, StatefulSetAwaitableResource::new);
        awaitableResourceFactory.registerFactory(DaemonSet.class, DaemonSetAwaitableResource::new);
    }

    private final Map<Class<HasMetadata>, MatchableResource<HasMetadata>> matchableResources = new HashMap<>();

    private <T extends HasMetadata> MatchableResource<T> matchableResource(
            @NonNull
            Class<T> type,
            MixedOperation<T, ? extends KubernetesResourceList<T>, ? extends Resource<T>> resourceAccessor
    ) {
        return (MatchableResource<T>) matchableResources.compute((Class<HasMetadata>)type, (t, matchableResource) -> {
            if(matchableResource == null) {
                matchableResource = (MatchableResource<HasMetadata>)new ExclusionOnlyMatchableResource<>(type);
            }
            return matchableResource.tryUpgrade((MixedOperation<HasMetadata, ? extends KubernetesResourceList<HasMetadata>, ? extends Resource<HasMetadata>>) resourceAccessor);
        });
    }

    private <T extends HasMetadata> NamespacedResourceMatcher<T> makeNamespacedResourceMatcher(ResourceMatcher<T> matcher) {
        if(matcher instanceof NamespacedResourceMatcher<T> namespacedResourceMatcher) {
            return namespacedResourceMatcher;
        }
        return new NamespacedResourceMatcher<>(matcher, client.getNamespace());
    }

    /**
     * Add wait for matching deployments to be ready
     * @param matcher Matcher for deployments
     */
    public KubernetesResourceWaiter deployments(ResourceMatcher<? super Deployment> matcher) {
        matchableResource(Deployment.class, client.apps().deployments()).addMatcher(makeNamespacedResourceMatcher(matcher));
        return this;
    }

    /**
     * Add wait for matching stateful sets to be ready
     * @param matcher Matcher for stateful sets
     */
    public KubernetesResourceWaiter statefulSets(ResourceMatcher<? super StatefulSet> matcher) {
        matchableResource(StatefulSet.class, client.apps().statefulSets()).addMatcher(makeNamespacedResourceMatcher(matcher));
        return this;
    }

    /**
     * Add wait for matching daemon sets to be ready
     * @param matcher Matcher for daemon sets
     */
    public KubernetesResourceWaiter daemonSets(ResourceMatcher<? super DaemonSet> matcher) {
        matchableResource(DaemonSet.class, client.apps().daemonSets()).addMatcher(makeNamespacedResourceMatcher(matcher));
        return this;
    }

    /**
     * Add wait for matching jobs to be finished
     * @param matcher Matcher for jobs
     */
    public KubernetesResourceWaiter jobs(ResourceMatcher<? super Job> matcher) {
        matchableResource(Job.class, client.batch().v1().jobs()).addMatcher(makeNamespacedResourceMatcher(matcher));
        return this;
    }

    /**
     * Exclude certain resources from the wait
     * @param clazz Resource type to exclude
     * @param matcher Matcher for the resource type
     */
    public <T extends HasMetadata> KubernetesResourceWaiter excluding(Class<T> clazz, ResourceMatcher<T> matcher) {
        matchableResource(clazz, null).addExclusion(matcher);
        return this;
    }

    /**
     * @return Stream of all resources that will be waited on
     */
    public Stream<? extends AwaitableResource> resources() {
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

    /**
     * @return Stream of resources that are waited on that are not yet ready
     */
    public Stream<? extends AwaitableResource> nonReadyResources() {
        return resources()
                .filter(awaitableResource -> !awaitableResource.isReady());

    }

    /**
     * Wait for all resources to be ready
     * @param configuration Configure {@link Awaitility}
     */
    public void await(UnaryOperator<ConditionFactory> configuration) {
        configuration.apply(
                        Awaitility.await()
                                .conditionEvaluationListener(new ConditionEvaluationListenerImpl())
                )
                .until(() -> this.nonReadyResources().toList(), Matchers.empty());

    }

    /**
     * Shut down all informers
     */
    @Override
    public void close() {
        for (var matchableResource : matchableResources.values()) {
            matchableResource.close();
        }
    }

    /**
     * Custom awaitility {@link ConditionEvaluationListener} that prints events and logs for resources that are not ready after the timeout
     */
    @RequiredArgsConstructor
    private static class ConditionEvaluationListenerImpl implements ConditionEvaluationListener<List<? extends AwaitableResource>> {
        private static final ConditionEvaluationListener<Object> LOGGER = new ConditionEvaluationLogger(log::info, TimeUnit.SECONDS);
        private final AtomicReference<List<? extends AwaitableResource>> lastFailingCondition = new AtomicReference<>();


        @Override
        public void conditionEvaluated(EvaluatedCondition<List<? extends AwaitableResource>> condition) {
            if(!condition.isSatisfied()) {
                lastFailingCondition.set(condition.getValue());
            } else {
                LOGGER.conditionEvaluated((EvaluatedCondition)condition);
            }
        }

        @Override
        public void beforeEvaluation(StartEvaluationEvent<List<? extends AwaitableResource>> startEvaluationEvent) {
            LOGGER.beforeEvaluation((StartEvaluationEvent) startEvaluationEvent);
        }

        @Override
        public void onTimeout(TimeoutEvent timeoutEvent) {
            log.info(timeoutEvent.getDescription());
            log.error("{} resources are not ready", lastFailingCondition.get().size());

            lastFailingCondition.get().forEach(resource -> {
                log.error(" - {}", resource);
            });

            for (var awaitableResource : lastFailingCondition.get()) {
                awaitableResource.events().forEachOrdered(event -> {
                    var logBuilder = switch (event.type()) {
                        case "Warning" -> log.atWarn();
                        default -> log.atInfo();
                    };
                    StringBuilder message = new StringBuilder("[{}] {}");
                    logBuilder = logBuilder
                            .addArgument(event.resource())
                            .addArgument(event.timestamp());
                    if(event.repeat().count() > 1) {
                        message.append(" ({}x over {})");
                        logBuilder = logBuilder.addArgument(event.repeat().count())
                                .addArgument(event.repeat().period());
                    }

                    message.append(": {} {} {}");
                    logBuilder.log(message.toString(),
                            event.type(),
                            event.reason(),
                            event.message()
                    );
                });
                awaitableResource.logs()
                        .forEachOrdered(line -> log.info("[{}] {} {} >>> {}", line.resource(), line.timestamp(), line.container(), line.line()));
            }
        }

        @Override
        public void exceptionIgnored(IgnoredException ignoredException) {
            LOGGER.exceptionIgnored(ignoredException);
        }
    }

}
