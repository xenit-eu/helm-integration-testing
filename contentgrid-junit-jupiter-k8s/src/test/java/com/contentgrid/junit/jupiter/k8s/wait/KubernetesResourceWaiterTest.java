package com.contentgrid.junit.jupiter.k8s.wait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.contentgrid.helm.HelmInstallCommand.InstallOption;
import com.contentgrid.junit.jupiter.helm.HelmChart;
import com.contentgrid.junit.jupiter.helm.HelmChartHandle;
import com.contentgrid.junit.jupiter.helm.HelmClient;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@KubernetesTestCluster
@HelmClient
class KubernetesResourceWaiterTest {

    static KubernetesClient kubernetesClient;

    @HelmChart(chart = "classpath:chart")
    HelmChartHandle resourceWaiterChart;

    @Test
    void waitForChart() {
        var installResult = resourceWaiterChart.install();

        var waiter = new KubernetesResourceWaiter(kubernetesClient)
                .include(installResult)
                .exclude(Deployment.class, ResourceMatcher.named("broken-deploy"));

        assertThat(waiter.resources())
                .satisfiesExactlyInAnyOrder(
                        deployment -> {
                            assertThat(deployment.getApiType()).isEqualTo(Deployment.class);
                            assertThat(deployment.getObjectReference().getNamespace()).isEqualTo("kube-system");
                            assertThat(deployment.getObjectReference().getName()).isEqualTo("test-nginx-deploy");
                        },
                        daemonSet -> {
                            assertThat(daemonSet.getApiType()).isEqualTo(DaemonSet.class);
                            assertThat(daemonSet.getObjectReference().getNamespace()).isEqualTo(kubernetesClient.getNamespace());
                            assertThat(daemonSet.getObjectReference().getName()).isEqualTo("test-nginx-daemonset");
                        },
                        statefulSet -> {
                            assertThat(statefulSet.getApiType()).isEqualTo(StatefulSet.class);
                            assertThat(statefulSet.getObjectReference().getNamespace()).isEqualTo(kubernetesClient.getNamespace());
                            assertThat(statefulSet.getObjectReference().getName()).isEqualTo("test-nginx-sts");
                        },
                        job -> {
                            assertThat(job.getApiType()).isEqualTo(Job.class);
                            assertThat(job.getObjectReference().getNamespace()).isEqualTo(kubernetesClient.getNamespace());
                            assertThat(job.getObjectReference().getName()).isEqualTo("test-job");
                        }
                )
        ;

        waiter.await(await -> await.atMost(1, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS));

        // Check events and logs from a job
        assertThat(waiter.resources())
                .filteredOn(resource -> resource.getObjectReference().getName().equals("test-job"))
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.events())
                            // Event of the job itself
                            .anySatisfy(event -> {
                                assertThat(event.resource().getApiType()).isEqualTo(Job.class);
                                assertThat(event.type()).isEqualTo("Normal");
                                assertThat(event.reason()).isEqualTo("SuccessfulCreate");
                            })
                            // Event of the pod started by the job
                            .anySatisfy(event -> {
                                assertThat(event.resource().getApiType()).isEqualTo(Pod.class);
                                assertThat(event.type()).isEqualTo("Normal");
                                assertThat(event.reason()).isEqualTo("Started");
                            })
                    ;
                    assertThat(job.logs()).satisfiesExactly(line -> {
                        assertThat(line.resource().getApiType()).isEqualTo(Pod.class);
                        assertThat(line.container()).isEqualTo("hello");
                        assertThat(line.line()).isEqualTo("Hello from this job");
                    }, line -> {
                        assertThat(line.resource().getApiType()).isEqualTo(Pod.class);
                        assertThat(line.container()).isEqualTo("goodbye");
                        assertThat(line.line()).isEqualTo("Bye from this job");
                    });
                });

        waiter.close();
    }

    @Test
    void waitForBrokenDeploy() {
        var installResult = resourceWaiterChart.install();

        var waiter = new KubernetesResourceWaiter(kubernetesClient)
                .include(Deployment.class, ResourceMatcher.named("broken-deploy"));

        Logger waiterLogger = (Logger) LoggerFactory.getLogger(KubernetesResourceWaiter.class);
        var testAppender = new TestListAppender();
        testAppender.start();
        var prevLevel = waiterLogger.getLevel();
        waiterLogger.setLevel(Level.INFO);
        waiterLogger.addAppender(testAppender);


        try {
            assertThatCode(() -> {
                waiter.await(await -> await.atMost(30, TimeUnit.SECONDS));
            }).hasMessageContaining("Deployment " + kubernetesClient.getNamespace() + "/broken-deploy");
        } finally {
            waiterLogger.setLevel(prevLevel);
            waiterLogger.detachAppender(testAppender);
            testAppender.stop();
        }

        assertThat(testAppender.getEvents())
                .extracting(ILoggingEvent::toString)
                .anySatisfy(message -> assertThat(message).contains("[ERROR] 1 resources are not ready"))
                .anySatisfy(message -> assertThat(message).contains("[ERROR]  - Deployment " + kubernetesClient.getNamespace() + "/broken-deploy"))
                // Event about failing readiness probe
                .anySatisfy(message -> assertThat(message).contains("Warning Unhealthy Readiness probe failed: HTTP probe failed with statuscode: 404"))
                // Log of nginx access log message (from readiness probe)
                .anySatisfy(message -> assertThat(message).contains("\"GET /ready HTTP/1.1\" 404"));
        ;

        waiter.close();
    }

    @Test
    void matchOnLabels() {
        resourceWaiterChart.install(InstallOption.namespace("kube-system"));

        try(var waiter = new KubernetesResourceWaiter(kubernetesClient)
                .include(Deployment.class, ResourceMatcher.labelled(Map.of(
                        "type", "webserver"
                )).inNamespace("kube-system"))) {

            assertThat(waiter.resources()).hasSize(2); // Both our deployments match label type=webserver
        }

        try(var waiter = new KubernetesResourceWaiter(kubernetesClient)
                .include(Deployment.class, ResourceMatcher.labelled(Map.of(
                        "app", "nginx"
                )).inNamespace("kube-system"))) {

            assertThat(waiter.resources()).hasSize(1); // Only the nginx deployment matches
        }

    }

    /**
     * A simple Logback appender that stores logging events in a list for testing.
     */
    public static class TestListAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = Collections.synchronizedList(new LinkedList<>());

        @Override
        protected void append(ILoggingEvent eventObject) {
            events.add(eventObject);
        }

        public List<ILoggingEvent> getEvents() {
            // Return a new list to prevent ConcurrentModificationException if the list is modified while iterating
            return new LinkedList<>(events);
        }

        public void clearEvents() {
            events.clear();
        }
    }
}