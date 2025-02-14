package com.contentgrid.testcontainers.k3s;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
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

                // Making sure that creating a PVC and a Pod works
                PersistentVolumeClaim persistentVolumeClaim = new PersistentVolumeClaimBuilder()
                        .withNewMetadata().withName("test-pv-claim").withNamespace("default").endMetadata()
                        .withNewSpec()
                        .withAccessModes("ReadWriteOnce")
                        .withNewResources()
                        .addToRequests("storage", new Quantity("1Gi"))
                        .endResources()
                        .endSpec()
                        .build();
                client.persistentVolumeClaims().resource(persistentVolumeClaim).create();

                // verify that we can start a pod
                var helloworld = dummyStartablePod(persistentVolumeClaim.getMetadata().getName());
                client.pods().resource(helloworld).create();
                client.pods().inNamespace("default").withName("helloworld").waitUntilReady(30, TimeUnit.SECONDS);

                assertThat(client.pods().inNamespace("default").withName("helloworld"))
                        .extracting(Resource::isReady)
                        .isEqualTo(true);
            }
        }
    }

    private Pod dummyStartablePod(String pvcName) {

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
                .withVolumes(List.of(
                        new VolumeBuilder()
                                .withName("test-volume")
                                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder().withClaimName(
                                        pvcName).build())
                                .build()
                ))
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