package com.contentgrid.testcontainers.k3s.customizer;

import com.contentgrid.junit.jupiter.k8s.wait.KubernetesResourceWaiter;
import com.contentgrid.junit.jupiter.k8s.wait.ResourceMatcher;
import com.contentgrid.testcontainers.k3s.CustomizableK3sContainer;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public abstract class AbstractK3sContainerCustomizerTest {
    private final List<Startable> containers = new LinkedList<>();

    protected KubernetesClient createContainer(Consumer<K3sContainerCustomizers> configuration) {
        var container = new CustomizableK3sContainer(DockerImageName.parse("rancher/k3s:v1.33.1-k3s1"));
        configuration.accept(container);
        containers.add(container);
        container.withLogConsumer(new Slf4jLogConsumer(log, true));
        container.start();
        var config = Config.fromKubeconfig(container.getKubeConfigYaml());

        return new KubernetesClientBuilder().withConfig(config).build();
    }

    protected void assertScript(KubernetesClient kubernetesClient, String image, String shell) {
        var ns = kubernetesClient.namespaces()
                .resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withGenerateName("run-script-")
                        .endMetadata()
                        .build())
                .create();



        var configmap = kubernetesClient.configMaps()
                .inNamespace(ns.getMetadata().getName())
                .resource(new ConfigMapBuilder()
                        .withNewMetadata()
                        .withGenerateName("run-script-")
                        .endMetadata()
                        .addToData("script.sh", shell)
                        .build())
                .create();
        var job = kubernetesClient.batch().v1().jobs()
                .inNamespace(ns.getMetadata().getName())
                .resource(new JobBuilder()
                        .withNewMetadata()
                        .withGenerateName("run-script-")
                        .endMetadata()
                        .withNewSpec()
                        .withNewTemplate()
                        .withNewSpec()
                        .withRestartPolicy("OnFailure")
                        .addNewContainer()
                        .withName("test")
                        .withImage(image)
                        .withCommand("bash", "/opt/script.sh")
                        .addNewVolumeMount()
                        .withName("script")
                        .withMountPath("/opt")
                        .endVolumeMount()
                        .endContainer()
                        .addNewVolume()
                        .withName("script")
                        .withNewConfigMap()
                        .withName(configmap.getMetadata().getName())
                        .endConfigMap()
                        .endVolume()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build()
                )
                .create();

        new KubernetesResourceWaiter(kubernetesClient)
                .jobs(ResourceMatcher.named(job.getMetadata().getName()).inNamespace(ns.getMetadata().getName()))
                .await(await -> await.atMost(1, TimeUnit.MINUTES))
                .close();

        kubernetesClient.namespaces().withName(ns.getMetadata().getName()).delete();
    }

    @AfterEach
    void shutdownContainers() {
        containers.forEach(Startable::stop);
        containers.clear();
    }

}
