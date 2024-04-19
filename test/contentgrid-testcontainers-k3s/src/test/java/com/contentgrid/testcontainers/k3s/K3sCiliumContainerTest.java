package com.contentgrid.testcontainers.k3s;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@Slf4j
public class K3sCiliumContainerTest {

    @Test
    @Disabled("expensive test (~80 seconds)")
    public void shouldStartAndHaveListableNode() {
        try (var container = new K3sCiliumContainer()) {
            container
                    .withLogConsumer(new Slf4jLogConsumer(log))
                    .start();

            var kubeConfigYaml = container.getKubeConfigYaml();
            var config = Config.fromKubeconfig(kubeConfigYaml);

            try (var client = new KubernetesClientBuilder().withConfig(config).build()) {

                List<Node> nodes = client.nodes().list().getItems();

                assertThat(nodes).hasSize(1);

                // verify that we can start a pod
                var helloworld = dummyStartablePod();
                client.pods().resource(helloworld).create();
                client.pods().inNamespace("default").withName("helloworld").waitUntilReady(30, TimeUnit.SECONDS);

                assertThat(client.pods().inNamespace("default").withName("helloworld"))
                        .extracting(Resource::isReady)
                        .isEqualTo(true);
            }
        }
    }

    private Pod dummyStartablePod() {
        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(
                        new ContainerBuilder()
                                .withName("helloworld")
                                .withImage("testcontainers/helloworld:1.1.0")
                                .withPorts(new ContainerPortBuilder().withContainerPort(8080).build())
                                .withReadinessProbe(
                                        new ProbeBuilder().withNewTcpSocket().withNewPort(8080).endTcpSocket().build())
                                .build()
                )
                .build();

        return new PodBuilder()
                .withNewMetadata()
                .withName("helloworld")
                .withNamespace("default")
                .endMetadata()
                .withSpec(podSpec)
                .build();
    }
}