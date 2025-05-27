package com.contentgrid.junit.jupiter.helm;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.helm.Helm;
import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import io.fabric8.kubernetes.api.model.Namespace;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HelmClientExtensionTest {

    @Nested
    @HelmClient
    class AutowiringTest {

        static Helm staticHelm;

        Helm instanceHelm;

        @Test
        void staticHelmClient() {
            assertThat(staticHelm).isNotNull();
        }

        @Test
        void instanceHelmClient() {
            assertThat(instanceHelm).isNotNull();
        }

        @Test
        void parameterHelmClient(Helm paramHelm) {
            assertThat(paramHelm).isNotNull();
        }
    }

    @Nested
    @KubernetesTestCluster
    @HelmClient
    class IntegrationTest {

        Helm helm;

        // random ephemeral namespace created by fabric8 junit-jupiter integration
        Namespace namespace;

        @Test
        void testInstall() {
            assertThat(helm.environment()).containsEntry("HELM_NAMESPACE", namespace.getMetadata().getName());

            assertThat(helm.list().releases()).isEmpty();
            var result = helm.install().chart("nginx", "oci://registry-1.docker.io/bitnamicharts/nginx");

            // verify it is using the namespace created by fabric8
            assertThat(result.namespace()).isEqualTo(namespace.getMetadata().getName());

            // verify we have a single release deployed
            assertThat(helm.list().releases()).singleElement().satisfies(release -> {
                assertThat(release.status()).isEqualTo("deployed");
            });

        }
    }

    @Nested
    @KubernetesTestCluster
    @HelmClient
    class HelmChartHandleTest {

        @HelmChart(chart = "oci://registry-1.docker.io/bitnamicharts/nginx")
        HelmChartHandle defaultChart;

        @HelmChart(chart = "oci://registry-1.docker.io/bitnamicharts/nginx", namespace = HelmChart.NAMESPACE_ISOLATE)
        HelmChartHandle isolatedChart;

        @HelmChart(chart = "oci://registry-1.docker.io/bitnamicharts/nginx", namespace = "kube-system")
        HelmChartHandle explicitNamespace;

        @HelmChart(chart = "classpath:fixtures/app", namespace = HelmChart.NAMESPACE_ISOLATE)
        HelmChartHandle appChart;

        // random ephemeral namespace created by fabric8 junit-jupiter integration
        Namespace namespace;

        @Test
        void testInstallHandles() {
            var defaultResult = defaultChart.install();
            var isolatedResult = isolatedChart.install();
            var explicitNamespaceResult = explicitNamespace.install();

            assertThat(defaultResult.namespace()).isEqualTo(namespace.getMetadata().getName());
            assertThat(isolatedResult.namespace()).isNotEqualTo(defaultResult.namespace());
            assertThat(explicitNamespaceResult.namespace()).isEqualTo("kube-system");
        }

        @Test
        void installClasspathHandle() {
            appChart.install();
        }
    }
}