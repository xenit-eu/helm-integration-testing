package com.contentgrid.junit.jupiter.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.contentgrid.junit.jupiter.k8s.KubernetesTestClusterExtensionTest.DummyK8s.DummyK8sClusterProvider;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestClusterExtensionTest.Unavailable.UnavailableK8sClusterProvider;
import com.contentgrid.junit.jupiter.k8s.providers.K3sTestcontainersClusterProvider;
import com.contentgrid.junit.jupiter.k8s.providers.KubernetesClusterProvider;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.engine.execution.NamespaceAwareStore;
import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testcontainers.DockerClientFactory;

class KubernetesTestClusterExtensionTest {

    @Test
    void testExtensionCondition() {
        ConditionEvaluationResult result = new KubernetesTestClusterExtension()
                .evaluateExecutionCondition(extensionContext(IntegrationTest.class));
        assertThat(result.isDisabled()).isFalse();
    }

    @Test
    void unavailableClusterProvider() {
        ConditionEvaluationResult result = new KubernetesTestClusterExtension()
                .evaluateExecutionCondition(extensionContext(Unavailable.class));
        assertThat(result.isDisabled()).isTrue();
    }

    @Test
    void missingAnnotation() {
        assertThatThrownBy(() -> new KubernetesTestClusterExtension()
                .evaluateExecutionCondition(extensionContext(KubernetesTestClusterExtensionTest.class)))
                .isInstanceOf(ExtensionConfigurationException.class);
    }

    @Test
    void restoreSystemProperty() throws Exception {
        var systemPropertyValue ="/tmp/kubeconfig";
        var systemPropertyName = "kubeconfig";
        System.setProperty(systemPropertyName, systemPropertyValue);

        var extension = new KubernetesTestClusterExtension();
        var context = extensionContext(DummyK8s.class);
        extension.beforeAll(context);
        // Fabric8 kubernetes client gets constructed from kubeconfig system property
        assertThat(System.getProperty(systemPropertyName)).isNotEqualTo(systemPropertyValue);

        extension.afterAll(context);
        assertThat(System.getProperty(systemPropertyName)).isEqualTo(systemPropertyValue);
    }


    @KubernetesTestCluster
    static class IntegrationTest {

        static KubernetesClient kubernetesClient;

        @Test
        void defaultKubernetesTestCluster() {
            // assert kubernetesClient in injected
            assertThat(kubernetesClient).isNotNull();

            // assert it points to your local docker daemon
            assertThat(kubernetesClient.getMasterUrl().getHost())
                    .isEqualTo(DockerClientFactory.instance().dockerHostIpAddress());

            // assert fabric8 client can connect to kubernetes
            assertThat(kubernetesClient.nodes().list().getItems()).hasSize(1);
        }
    }

    @KubernetesTestCluster(providers = UnavailableK8sClusterProvider.class)
    static class Unavailable {

        static class UnavailableK8sClusterProvider extends K3sTestcontainersClusterProvider {
            @Override
            public ProviderEvaluationResult evaluate() {
                return ProviderEvaluationResult.disabled("unavailable");
            }
        }
    }



    @KubernetesTestCluster(providers = DummyK8sClusterProvider.class)
    static class DummyK8s {
        static class DummyK8sClusterProvider implements KubernetesClusterProvider {

            @Override
            public ProviderEvaluationResult evaluate() {
                return ProviderEvaluationResult.enabled();
            }

            @Override
            public KubernetesProviderResult start() {
                return new KubernetesProviderResult() {
                    @Override
                    public String getKubeConfigYaml() {
                        return "";
                    }

                    @Override
                    public void close() {

                    }
                };
            }

            @Override
            public void stop() {

            }
        }
    }

    @SuppressWarnings("resource")
    private ExtensionContext extensionContext(Class clazz) {
        var valueStore = new NamespacedHierarchicalStore<Namespace>(null);

        ExtensionContext extensionContext = Mockito.mock(ExtensionContext.class);
        Mockito.when(extensionContext.getRequiredTestClass()).thenReturn(clazz);
        Mockito.when(extensionContext.getStore(any())).thenAnswer(
                (Answer<Store>) invocation -> new NamespaceAwareStore(valueStore, invocation.getArgument(0)));

        return extensionContext;
    }
}