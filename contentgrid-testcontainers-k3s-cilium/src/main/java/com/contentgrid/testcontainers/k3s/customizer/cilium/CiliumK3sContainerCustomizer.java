package com.contentgrid.testcontainers.k3s.customizer.cilium;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.PropagationMode;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.Volume;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.k3s.K3sContainer;

/**
 * Installs <a href="https://cilium.io">Cilium</a> as CNI instead of the default flannel CNI
 */
public class CiliumK3sContainerCustomizer implements K3sContainerCustomizer {

    @Override
    public void customize(K3sContainer container) {

        var command = Arrays.asList(container.getCommandParts());
        command.add("--tls-san=10.43.0.1"); // The kubernetes API
        command.addAll(List.of(
                // see https://docs.cilium.io/en/stable/installation/k8s-install-helm/
                "--flannel-backend=none",
                "--disable-network-policy",
                "--disable=metrics-server"
        ));
        container.setCommandParts(command.toArray(String[]::new));

        // Shared binds required by Cilium
        // https://github.com/rancher-sandbox/rancher-desktop/discussions/1977
        // > the cilium-agent container mounts these using host mounts,
        // > and it requires them to be SHARED mounts for this work.
        container.withCreateContainerCmdModifier(modifier -> {
            var hostConfig = Objects.requireNonNull(modifier.getHostConfig());
            hostConfig.withBinds(Stream.concat(
                    Arrays.stream(hostConfig.getBinds()),
                    Stream.of(
                            createSharedBind("/sys/fs/bpf", new Volume("/sys/fs/bpf")),
                            createSharedBind("/sys/fs/cgroup", new Volume("/run/cilium/cgroupv2"))
                    )).toList());
        });

        // Install Cilium with helm-controller
        container.withCopyToContainer(
                MountableFile.forClasspathResource(getClass().getResource("cilium.yaml").toExternalForm()),
                "/var/lib/rancher/k3s/server/manifests/cilium.yaml"
        );

        container.waitingFor(
                new LogMessageWaitStrategy().withRegEx(".*Controller detected that some Nodes are Ready.*")
                        .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
    }

    private static Bind createSharedBind(String path, Volume volume) {
        return new Bind(path, volume, AccessMode.rw, SELContext.DEFAULT, null, PropagationMode.SHARED);
    }
}
