package com.contentgrid.junit.jupiter.k8s.wait;

import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import com.contentgrid.junit.jupiter.k8s.resource.AwaitableResource;
import com.contentgrid.junit.jupiter.k8s.resource.ConfigurableResourceSet;
import com.contentgrid.junit.jupiter.k8s.resource.ResourceMatchingSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
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
@Slf4j
public class KubernetesResourceWaiter implements AutoCloseable, ResourceMatchingSpec<KubernetesResourceWaiter> {
    private final ConfigurableResourceSet resourceSet;
    private final KubernetesClient client;

    public KubernetesResourceWaiter(@NonNull KubernetesClient client) {
        this.client = client;
        resourceSet = ConfigurableResourceSet.of(client);
    }

    /**
     * Add wait for matching deployments to be ready
     * @param matcher Matcher for deployments
     */
    public KubernetesResourceWaiter deployments(com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher<? super Deployment> matcher) {
        return include(Deployment.class, matcher);
    }

    /**
     * Add wait for matching stateful sets to be ready
     * @param matcher Matcher for stateful sets
     */
    public KubernetesResourceWaiter statefulSets(com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher<? super StatefulSet> matcher) {
        return include(StatefulSet.class, matcher);
    }

    /**
     * Add wait for matching daemon sets to be ready
     * @param matcher Matcher for daemon sets
     */
    public KubernetesResourceWaiter daemonSets(com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher<? super DaemonSet> matcher) {
        return include(DaemonSet.class, matcher);
    }

    /**
     * Add wait for matching jobs to be finished
     * @param matcher Matcher for jobs
     */
    public KubernetesResourceWaiter jobs(com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher<? super Job> matcher) {
        return include(Job.class, matcher);
    }

    /**
     * Include all supported resources from a helm install to the wait
     * @param installResult The helm install result
     */
    public KubernetesResourceWaiter include(InstallResult installResult) {
        resourceSet.include(installResult);

        return this;
    }


    /**
     * Include certain resources to the wait
     * @param clazz Resource type to include
     * @param matcher Matcher for the resource type
     */
    public <T extends HasMetadata> KubernetesResourceWaiter include(
            @NonNull Class<T> clazz,
            @NonNull com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher<? super T> matcher
    ) {
        resourceSet.include(clazz, matcher);
        return this;
    }

    /**
     * Exclude certain resources from the wait
     * @param clazz Resource type to exclude
     * @param matcher Matcher for the resource type
     */
    public <T extends HasMetadata> KubernetesResourceWaiter exclude(
            @NonNull Class<T> clazz,
            @NonNull com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher<? super T> matcher
    ) {
        resourceSet.exclude(clazz, matcher);
        return this;
    }

    /**
     * @return Stream of all resources that will be waited on
     */
    public Stream<? extends AwaitableResource> resources() {
        return resourceSet.stream();
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
        resourceSet.close();
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
