package com.contentgrid.junit.jupiter.k8s.wait;

import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import com.contentgrid.junit.jupiter.k8s.wait.resource.AwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.AwaitableResourceFactory;
import com.contentgrid.junit.jupiter.k8s.wait.resource.DaemonSetAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.DeploymentAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.JobAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.PodAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.ReplicaSetAwaitableResource;
import com.contentgrid.junit.jupiter.k8s.wait.resource.StatefulSetAwaitableResource;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64InputStream;
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

    private static final Map<Class<? extends HasMetadata>, Function<KubernetesClient, MixedOperation<? extends HasMetadata, ? extends KubernetesResourceList<? extends HasMetadata>, ? extends Resource<? extends HasMetadata>>>> RESOURCE_ACCESSORS = Map.of(
            Deployment.class, client -> client.apps().deployments(),
            StatefulSet.class, client -> client.apps().statefulSets(),
            DaemonSet.class, client -> client.apps().daemonSets(),
            Job.class, client -> client.batch().v1().jobs(),
            ReplicaSet.class, client -> client.apps().replicaSets()
    );

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
            Class<T> type
    ) {
        return (MatchableResource<T>) matchableResources.computeIfAbsent((Class<HasMetadata>)type, (t) -> new MatchableResource<>(
                (MixedOperation<HasMetadata, ? extends KubernetesResourceList<HasMetadata>, ? extends Resource<HasMetadata>>) RESOURCE_ACCESSORS.get(t).apply(client)
        ));
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
        return include(Deployment.class, matcher);
    }

    /**
     * Add wait for matching stateful sets to be ready
     * @param matcher Matcher for stateful sets
     */
    public KubernetesResourceWaiter statefulSets(ResourceMatcher<? super StatefulSet> matcher) {
        return include(StatefulSet.class, matcher);
    }

    /**
     * Add wait for matching daemon sets to be ready
     * @param matcher Matcher for daemon sets
     */
    public KubernetesResourceWaiter daemonSets(ResourceMatcher<? super DaemonSet> matcher) {
        return include(DaemonSet.class, matcher);
    }

    /**
     * Add wait for matching jobs to be finished
     * @param matcher Matcher for jobs
     */
    public KubernetesResourceWaiter jobs(ResourceMatcher<? super Job> matcher) {
        return include(Job.class, matcher);
    }

    /**
     * Include all supported resources from a helm install to the wait
     * @param installResult The helm install result
     */
    @SneakyThrows(IOException.class)
    public KubernetesResourceWaiter include(InstallResult installResult) {
        var releaseRawData = client.secrets().inNamespace(installResult.namespace())
                .withName("sh.helm.release.v1."+installResult.name()+".v"+installResult.version())
                .require()
                .getData()
                .get("release");

        // A helm release manifest is a base64-encoded gzip blob.
        // We need to base64 decode once more to undo the base64 encoding of k8s secrets
        var releaseJsonData = new GZIPInputStream(new Base64InputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(releaseRawData))
        ));

        var releaseJson = new ObjectMapper().readTree(releaseJsonData);

        String yamlManifest = releaseJson.path("manifest").asText();

        List<? extends HasMetadata> resources = client.getKubernetesSerialization().unmarshal(yamlManifest);

        for (var resource : resources) {
            if(RESOURCE_ACCESSORS.containsKey(resource.getClass())) {
                include(
                        resource.getClass(),
                        ResourceMatcher.named(resource.getMetadata().getName())
                                // The default namespace that objects without a namespace get installed into
                                // is the namespace that the helm chart is installed in
                                .inNamespace(Objects.requireNonNullElse(resource.getMetadata().getNamespace(), installResult.namespace()))
                );
            }
        }

        return this;
    }


    /**
     * Include certain resources to the wait
     * @param clazz Resource type to include
     * @param matcher Matcher for the resource type
     */
    public <T extends HasMetadata> KubernetesResourceWaiter include(
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
    public <T extends HasMetadata> KubernetesResourceWaiter exclude(
            @NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher
    ) {
        matchableResource(clazz).addExclusion(matcher);
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
    public KubernetesResourceWaiter await(@NonNull UnaryOperator<ConditionFactory> configuration) {
        configuration.apply(
                        Awaitility.await()
                                .conditionEvaluationListener(new ConditionEvaluationListenerImpl())
                )
                .until(() -> this.nonReadyResources().toList(), Matchers.empty());

        return this;
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
    private class ConditionEvaluationListenerImpl implements ConditionEvaluationListener<List<? extends AwaitableResource>> {
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
            log.info("Waiting for <{}>", resources().toList());
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
