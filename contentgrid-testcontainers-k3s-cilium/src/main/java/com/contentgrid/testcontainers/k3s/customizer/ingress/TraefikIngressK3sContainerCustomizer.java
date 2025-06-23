package com.contentgrid.testcontainers.k3s.customizer.ingress;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports.Binding;
import java.util.Arrays;
import lombok.EqualsAndHashCode;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Installs <a href="https://traefik.io/">Traefik</a> as an ingress controller,
 * with a fixed binding for HTTP to port 80 on the host
 */
public class TraefikIngressK3sContainerCustomizer implements K3sContainerCustomizer {

    @Override
    public void customize(K3sContainer container) {
        var command = Arrays.asList(container.getCommandParts());
        command.remove("--disable=traefik");
        container.setCommandParts(command.toArray(String[]::new));

        // Configure traefik
        // exposing traefik on fixed port 80 on the host - traefik-config.yaml
        // ideally, we should get rid of the fixed port mapping - problems:
        // - keycloak auth url + keycloak redirect configuration
        container.withCreateContainerCmdModifier(createContainerCmd -> {
             createContainerCmd.getHostConfig().getPortBindings().bind(
                     new ExposedPort(32080),
                     Binding.bindPort(80)
             );
        });
        container.withCopyToContainer(
                MountableFile.forClasspathResource(getClass().getResource("traefik-config.yaml").toExternalForm()),
                "/var/lib/rancher/k3s/server/manifests/traefik-config.yaml"
        );
    }
}
