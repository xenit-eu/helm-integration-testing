package com.contentgrid.junit.jupiter.k8s.resource;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.junit.jupiter.docker.registry.DockerRegistryCache;
import com.contentgrid.junit.jupiter.helm.HelmChart;
import com.contentgrid.junit.jupiter.helm.HelmChartHandle;
import com.contentgrid.junit.jupiter.helm.HelmClient;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import com.contentgrid.junit.jupiter.k8s.wait.KubernetesResourceWaiter;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@KubernetesTestCluster
@HelmClient
@DockerRegistryCache(name = "docker.io", proxy = "https://registry-1.docker.io")
class JobAwaitableResourceTest {
    static KubernetesClient kubernetesClient;

    @HelmChart(chart = "classpath:chart")
    HelmChartHandle chart;

    @Test
    void logsWithTimestamps() {
        chart.install();
        new KubernetesResourceWaiter(kubernetesClient)
                .jobs(ResourceMatcher.named("test-job"))
                .await(wait -> wait.atMost(3, TimeUnit.MINUTES));

        var resources = ConfigurableResourceSet.of(kubernetesClient)
                .include(Job.class, ResourceMatcher.named("test-job"));

        assertThat(resources.stream())
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.logs()).satisfiesExactly(
                            line -> {
                                assertThat(line.container()).isEqualTo("hello");
                                assertThat(line.timestamp()).isNotNull();
                                assertThat(line.line()).isEqualTo("Hello from this job");
                            },
                            line -> {
                                assertThat(line.container()).isEqualTo("goodbye");
                                assertThat(line.timestamp()).isNotNull();
                                assertThat(line.line()).isEqualTo("Hi");
                            },
                            line -> {
                                assertThat(line.container()).isEqualTo("goodbye");
                                assertThat(line.timestamp()).isNotNull();
                                assertThat(line.line()).isEqualTo("Buh");
                            },
                            line -> {
                                assertThat(line.container()).isEqualTo("goodbye");
                                assertThat(line.timestamp()).isNotNull();
                                assertThat(line.line()).isEqualTo("Buh Bye");
                            }
                    );

                });


    }

}