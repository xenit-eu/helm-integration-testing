package com.contentgrid.junit.jupiter.k8s.resource;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.helm.HelmInstallCommand.InstallOption;
import com.contentgrid.junit.jupiter.helm.HelmChart;
import com.contentgrid.junit.jupiter.helm.HelmChartHandle;
import com.contentgrid.junit.jupiter.helm.HelmClient;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

@KubernetesTestCluster
@HelmClient
class ConfigurableResourceSetImplTest {

    static KubernetesClient kubernetesClient;

    @HelmChart(chart = "classpath:chart")
    HelmChartHandle chart;

    @Test
    void chartResources() {
            var installResult = chart.install();

            var resourceSet = new ConfigurableResourceSetImpl(kubernetesClient)
                    .include(installResult);

            assertThat(resourceSet.stream())
                    .satisfiesExactlyInAnyOrder(
                            deployment -> {
                                assertThat(deployment.getApiType()).isEqualTo(Deployment.class);
                                assertThat(deployment.getObjectReference().getNamespace()).isEqualTo("kube-system");
                                assertThat(deployment.getObjectReference().getName()).isEqualTo("test-nginx-deploy");
                            },
                            deployment -> {
                                assertThat(deployment.getApiType()).isEqualTo(Deployment.class);
                                assertThat(deployment.getObjectReference().getNamespace()).isEqualTo(kubernetesClient.getNamespace());
                                assertThat(deployment.getObjectReference().getName()).isEqualTo("broken-deploy");
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
                    );
    }

    @Test
    void matchOnLabels() {
        chart.install(InstallOption.namespace("kube-system"));

        try(var resourceSet = new ConfigurableResourceSetImpl(kubernetesClient)
                .include(Deployment.class, ResourceMatcher.labelled(Map.of(
                        "type", "webserver"
                )).inNamespace("kube-system"))) {

            assertThat(resourceSet.stream()).hasSize(2); // Both our deployments match label type=webserver
        }

        try(var resourceSet = new ConfigurableResourceSetImpl(kubernetesClient)
                .include(Deployment.class, ResourceMatcher.labelled(Map.of(
                        "app", "nginx"
                )).inNamespace("kube-system"))) {

            assertThat(resourceSet.stream()).hasSize(1); // Only the nginx deployment matches
        }
    }
}