package com.contentgrid.testcontainers.k3s.customizer.cilium;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Installs Cilium CNI using {@link CiliumK3sContainerCustomizer} and installs a global default-deny network policy
 */
public class DefaultDenyCiliumK3sContainerCustomizer implements K3sContainerCustomizer {

    @Override
    public void onRegister(K3sContainerCustomizers customizers) {
        customizers.configure(CiliumK3sContainerCustomizer.class);
    }

    @Override
    public void customize(K3sContainer container) {
        container.withCopyToContainer(
                MountableFile.forClasspathResource(getClass().getResource("cilium-default-deny-all.yaml").toExternalForm()),
                "/var/lib/rancher/k3s/server/manifests/cilium-default-deny-all.yaml"
        );
    }
}
