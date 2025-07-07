package com.contentgrid.testcontainers.k3s;

import com.contentgrid.testcontainers.k3s.customizer.ClusterDomainsK3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.cilium.CiliumK3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.cilium.DefaultDenyCiliumK3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.ingress.TraefikIngressK3sContainerCustomizer;
import java.util.List;
import java.util.Set;
import org.testcontainers.utility.DockerImageName;

/**
 * @deprecated Use {@link CustomizableK3sContainer} with some {@link com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer}s instead.
 */
@Deprecated(since = "0.1.0", forRemoval = true)
public class K3sCiliumContainer extends CustomizableK3sContainer {

    public K3sCiliumContainer() {
        this(DockerImageName.parse("rancher/k3s:v1.29.3-k3s1"), false);
    }

    public K3sCiliumContainer(DockerImageName k3sDockerImage, boolean defaultDeny) {
        super(k3sDockerImage);
        configure(CiliumK3sContainerCustomizer.class);
        configure(TraefikIngressK3sContainerCustomizer.class);
        if(defaultDeny) {
            configure(DefaultDenyCiliumK3sContainerCustomizer.class);
        }
    }

    public K3sCiliumContainer withClusterDomains(String... domains) {
        configure(ClusterDomainsK3sContainerCustomizer.class, it -> it.withDomains(Set.of(domains)));
        return this;
    }

}
