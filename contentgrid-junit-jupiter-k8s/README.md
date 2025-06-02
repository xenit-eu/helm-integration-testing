# JUnit Jupiter Extensions for Kubernetes and Helm Testing

This project provides a set of JUnit Jupiter extensions to streamline testing with Kubernetes and Helm. It offers annotations for simplifying the setup and interaction with Kubernetes clusters, Helm clients, Docker registries, and external secret stores.

## Features

*   **`@KubernetesTestCluster`:** Simplifies testing against a Kubernetes cluster. It supports different cluster providers (e.g., K3s, Kind) and automatically configures the Kubernetes client.
*   **`@HelmClient`:** Injects a pre-configured `Helm` client into your tests, making it easy to interact with Helm deployments. Integrates seamlessly with `@KubernetesTestCluster` for end-to-end testing.
*   **`@HelmChart`:** Simplifies Helm chart installation by automatically copying the specified chart to a temporary directory and making it available to the injected `Helm` client.
*   **`@DockerRegistryCache`:** Starts a local Docker registry mirroring the specified remote registry. Useful for caching images and speeding up tests.  Integrates with `@KubernetesTestCluster` to automatically configure the cluster to use the local registry mirror.
*   **`@FakeSecretStore`:** Provides a fake secret store implementation for testing external secret integrations.  Allows you to easily add secrets and verify that your application correctly retrieves them.

## Usage

### Adding Dependencies

Add the following dependencies to your project's `build.gradle` (or equivalent):

```groovy
testImplementation 'com.contentgrid.helm.integrationtesting:contentgrid-junit-jupiter-k8s:...'
```

### `@KubernetesTestCluster`

Annotate a test class to use a Kubernetes cluster. The extension will automatically start a cluster (e.g., K3s) and configure the Kubernetes client.
For debugging, if you want to interact with the Kubernetes cluster, you can find the kubeconfig in 'tmp':
```bash
ls -ltr /tmp/kubeconfig*.yml
```

```java
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@KubernetesTestCluster
class MyKubernetesTest {

    static KubernetesClient kubernetesClient; // Injected Kubernetes client

    @Test
    void testKubernetesClient() {
        assertThat(kubernetesClient).isNotNull();
        // Interact with the Kubernetes cluster using the client
    }
}
```

### `@HelmClient`

Injects a `Helm` client.  Can be used standalone or with `@KubernetesTestCluster`.

```java
import com.contentgrid.helm.Helm;
import com.contentgrid.junit.jupiter.helm.HelmClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@HelmClient
class MyHelmTest {

    Helm helm; // Injected Helm client

    @Test
    void testHelmClient() {
        assertThat(helm).isNotNull();
        // Use the Helm client
    }
}

@KubernetesTestCluster
@HelmClient
class MyHelmKubernetesTest {
    Helm helm;

    @Test
    void testHelmWithKubernetes() {
        assertThat(helm).isNotNull();
        // Use the Helm client with the Kubernetes cluster
    }
}
```

### `@HelmChart`

HelmChart is an annotation that works together with the HelmClient JUnit Jupiter extension to install helm charts.

This annotation requires @HelmClient, which injects a configured Helm instance into
JUnit Jupiter test instance fields, static fields, and method arguments.

The referenced chart gets copied to the temporary working directory of the Helm client and chart repositories 
are installed in the ephemeral repository list of the Helm client.

```java
import com.contentgrid.helm.Helm;
import com.contentgrid.junit.jupiter.helm.HelmClient;
import com.contentgrid.junit.jupiter.helm.HelmChart;
import com.contentgrid.junit.jupiter.helm.HelmChartHandle;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@HelmClient
class MyHelmChartTest {

    Helm helm;

    // Path to your Helm chart, installed in the default namespace
    @HelmChart(chart = "file:src/test/resources/fixtures/app") 
    static HelmChartReference appChart;

    // External helm chart
    @HelmChart(chart = "oci://registry-1.docker.io/bitnamicharts/nginx", namespace = HelmChart.NAMESPACE_ISOLATE)
    static HelmChartHandle nginxChart;
    
    @BeforeAll
    static void installCharts() {
        nginxChart.install();
        appChart.install();
    }

    @Test
    void testHelmChart() {
        assertThat(helm).isNotNull();
        // Test your Helm chart
    }
}
```

### `@DockerRegistryCache`

Starts a local Docker registry mirror.

```java
import com.contentgrid.junit.jupiter.docker.registry.DockerRegistryCache;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import org.testcontainers.DockerClientFactory;

@DockerRegistryCache(name = "docker.io", proxy = "https://registry-1.docker.io")
class MyDockerRegistryTest {

    @Test
    void testDockerRegistryMirror() {
        // Your tests that use the Docker registry mirror
        assertThat(DockerClientFactory.instance().dockerHostIpAddress()).isNotBlank();
    }
}

@KubernetesTestCluster
@DockerRegistryCache(name = "docker.io", proxy = "https://registry-1.docker.io")
class MyDockerRegistryKubernetesTest {
    @Test
    void testDockerRegistryMirrorWithKubernetes() {
        // Your tests that use the Docker registry mirror with Kubernetes
    }
}
```

### `@FakeSecretStore`

Provides a fake ExternalSecretStore for testing.

```java
import com.contentgrid.junit.jupiter.externalsecrets.FakeSecretStore;
import com.contentgrid.junit.jupiter.externalsecrets.SecretStore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

@FakeSecretStore
class MySecretStoreTest {

    SecretStore secretStore;

    @Test
    void testSecretStore() {
        assertEquals("fake-secret-store", secretStore.getName());
        secretStore.addSecrets(Map.of("mysecret", "myvalue"));
        // Your tests that use the secret store
    }
}
```

## Examples

The included test classes provide comprehensive examples of how to use each extension. Refer to these tests for more detailed usage scenarios.

## Further Information

For more information about the underlying libraries used by these extensions, please refer to their respective documentations:

*   **fabric8-junit-jupiter:** For `@KubernetesTestCluster`
*   **Helm Java SDK:** For `Helm` client interaction
*   **Testcontainers:** For Docker interactions (including registry mirroring)
*   **External Secrets Operator:** For external secret concepts.

