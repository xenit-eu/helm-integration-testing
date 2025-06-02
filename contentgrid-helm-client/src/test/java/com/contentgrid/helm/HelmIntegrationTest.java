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
class HelmIntegrationTest {

    static Namespace namespace;

    static KubernetesClient kubernetesClient;

    static Helm helm;

    @BeforeAll
    static void setup() {
        // @KubernetesTestCluster loaded kubeconfig
        var kubeconfig = Path.of(System.getProperty("kubeconfig"));
        assertThat(kubeconfig).exists();

        helm = Helm.builder()
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

        assertThat(result.name()).isEqualTo("nginx");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.namespace()).isEqualTo(namespace.getMetadata().getName());

        var pods = kubernetesClient.pods().withLabel("foo", "bar").list().getItems();
        Assertions.assertThat(pods).hasSize(1);

        var releases = helm.list().releases();
        assertThat(releases).singleElement().satisfies(release -> {
            assertThat(release.name()).isEqualTo("nginx");
            assertThat(release.namespace()).isEqualTo(namespace.getMetadata().getName());
            assertThat(release.status()).isEqualTo("deployed");
        });

        helm.uninstall().uninstall("nginx");

        assertThat(helm.list().releases()).isEmpty();
    }

    @Test
    void installOCIWithGeneratedName() {
        var result = helm.install()
                .chart("oci://registry-1.docker.io/bitnamicharts/nginx:20.0.5");


        helm.uninstall().uninstall(result.name());
    }
}