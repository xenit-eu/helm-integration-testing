package com.contentgrid.testcontainers.k3s;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.PropagationMode;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.Volume;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class K3sCiliumContainer extends K3sContainer {

    public K3sCiliumContainer() {
        this(DockerImageName.parse("rancher/k3s:v1.29.3-k3s1"), false, false);
    }

    public K3sCiliumContainer(DockerImageName k3sDockerImage, boolean defaultDeny, boolean userSpaceCoreDNS) {
        super(k3sDockerImage);

        this.setCommand("server",
                "--tls-san=" + this.getHost(),
                "--tls-san=10.43.0.1", // the kubernetes API

                // see https://docs.cilium.io/en/stable/installation/k8s-install-helm/
                "--flannel-backend=none",
                "--disable-network-policy",
                "--disable=metrics-server"
        );

        // Shared binds required by Cilium
        // https://github.com/rancher-sandbox/rancher-desktop/discussions/1977
        // > the cilium-agent container mounts these using host mounts,
        // > and it requires them to be SHARED mounts for this work.
        this.withCreateContainerCmdModifier(modifier -> {
            var hostConfig = Objects.requireNonNull(modifier.getHostConfig());
            hostConfig.withBinds(Stream.concat(
                    Arrays.stream(hostConfig.getBinds()),
                    Stream.of(
                            createSharedBind("/sys/fs/bpf", new Volume("/sys/fs/bpf")),
                            createSharedBind("/sys/fs/cgroup", new Volume("/run/cilium/cgroupv2"))
                    )).toList());
        });

        // Configure traefik
        // exposing traefik on fixed port 80 on the host - see k3s/manifests/traefik-config.yaml
        // ideally, we should get rid of the fixed port mapping - problems:
        // - keycloak auth url + keycloak redirect configuration
        this.addFixedExposedPort(80, 32080, InternetProtocol.TCP);
        this.withCopyToContainer(
                MountableFile.forClasspathResource("k3s/manifests/traefik-config.yaml"),
                "/var/lib/rancher/k3s/server/manifests/traefik-config.yaml");

        // Install Cilium with helm-controller
        this.withCopyToContainer(
                MountableFile.forClasspathResource("k3s/manifests/cilium.yaml"),
                "/var/lib/rancher/k3s/server/manifests/cilium.yaml");

        if (userSpaceCoreDNS) {
            // user-space coredns config
            this.withCopyToContainer(
                    MountableFile.forClasspathResource("k3s/manifests/coredns-config.yaml"),
                    "/var/lib/rancher/k3s/server/manifests/coredns-config.yaml"
            );
        }

        if (defaultDeny) {
            // user-space default-deny-all network policy via k3s manifest
            this.withCopyToContainer(
                    MountableFile.forClasspathResource("k3s/manifests/cilium-default-deny-all.yaml"),
                    "/var/lib/rancher/k3s/server/manifests/cilium-default-deny-all.yaml"
            );
        }

        this.waitingFor(
                new LogMessageWaitStrategy().withRegEx(".*Controller detected that some Nodes are Ready.*")
                        .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));

    }

    private static Bind createSharedBind(String path, Volume volume) {
        return new Bind(path, volume, AccessMode.rw, SELContext.DEFAULT, null, PropagationMode.SHARED);
    }

}
