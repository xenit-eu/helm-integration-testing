package com.contentgrid.testcontainers.k3s.customizer.ingress;

import com.contentgrid.testcontainers.k3s.customizer.CustomizerUtils;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import com.contentgrid.testcontainers.k3s.customizer.WaitStrategyCustomizer;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports.Binding;
import java.util.ArrayList;
import java.util.Arrays;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.k3s.K3sContainer;

/**
 * Installs <a href="https://traefik.io/">Traefik</a> as an ingress controller,
 * with a fixed binding for HTTP to port 80 on the host
 */
public class TraefikIngressK3sContainerCustomizer implements K3sContainerCustomizer {

    @Override
    public void onRegister(K3sContainerCustomizers customizers) {
        customizers.configure(WaitStrategyCustomizer.class, wait -> wait.withAdditionalWaitStrategy(Wait.forLogMessage(".*\"Observed pod startup duration\" pod=\"kube-system/traefik-.*", 1)));
    }

    @Override
    public void customize(K3sContainer container) {
        // List implementation from Arrays.asList does not support modifications
        var command = new ArrayList<>(Arrays.asList(container.getCommandParts()));
        command.remove("--disable=traefik");
        container.setCommandParts(command.toArray(String[]::new));

        // Configure traefik
        // exposing traefik on fixed port 80 on the host - traefik-config.yaml
        // ideally, we should get rid of the fixed port mapping - problems:
        // - keycloak auth url + keycloak redirect configuration
        container.addExposedPort(32080);
        container.withCreateContainerCmdModifier(createContainerCmd -> {
            createContainerCmd.getHostConfig().getPortBindings().bind(
                    new ExposedPort(32080),
                     Binding.bindPort(80)
             );
        });
        container.withCopyToContainer(
                CustomizerUtils.forClassResource(this.getClass(), "traefik-config.yaml"),
                "/var/lib/rancher/k3s/server/manifests/traefik-config.yaml"
        );
    }
}
