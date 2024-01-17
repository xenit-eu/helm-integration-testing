package com.contentgrid.helm;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.helm.HelmInstallCommand.InstallOption;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.nio.file.Path;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@KubernetesTestCluster
class HelmClientIntegrationTest {

    static Namespace namespace;

    static KubernetesClient kubernetesClient;

    static HelmClient helm;

    @BeforeAll
    static void setup() {
        // @KubernetesTestCluster loaded kubeconfig
        var kubeconfig = Path.of(System.getProperty("kubeconfig"));
        assertThat(kubeconfig).exists();

        helm = HelmClient.builder()
                .withKubeConfig(kubeconfig)
                .withNamespace(namespace.getMetadata().getName())
                .build();
    }

    @Test
    void integrationTest() {

        var result = helm.install()
                .chart("nginx", "oci://registry-1.docker.io/bitnamicharts/nginx",
                        InstallOption.values(Map.of(
                                "commonLabels", Map.of("foo", "bar")
                        )));

        assertThat(result.getName()).isEqualTo("nginx");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getNamespace()).isEqualTo(namespace.getMetadata().getName());

        var pods = kubernetesClient.pods().withLabel("foo", "bar").list().getItems();
        Assertions.assertThat(pods).hasSize(1);

        var releases = helm.list().releases();
        assertThat(releases).singleElement().satisfies(release -> {
            assertThat(release.name()).isEqualTo("nginx");
            assertThat(release.namespace()).isEqualTo(namespace.getMetadata().getName());
            assertThat(release.status()).isEqualTo("deployed");
        });
    }
}