package com.contentgrid.junit.jupiter.k8s.providers;

import com.contentgrid.testcontainers.k3s.customizer.cilium.DefaultDenyCiliumK3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.ingress.TraefikIngressK3sContainerCustomizer;

/**
 * Use a customized variant of {@link K3sTestcontainersClusterProvider}
 * <code>
 *     class CustomClusterProvider extends K3sTestcontainersClusterProvider {
 *          public CustomClusterProvider() {
 *              super();
 *              configure(DefaultDenyCiliumK3sContainerCustomizer.class);
 *              configure(TraefikIngressK3sContainerCustomizer.class);
 *          }
 *     }
 * </code>
 *
 * @deprecated Replaced by the more flexible {@link com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer} system
 */
@Deprecated(since = "0.1.0", forRemoval = true)
public class K3sCiliumDefaultDenyCoreDNSClusterProvider extends K3sTestcontainersClusterProvider {

    public K3sCiliumDefaultDenyCoreDNSClusterProvider() {
        super(
                new DefaultDenyCiliumK3sContainerCustomizer(),
                new TraefikIngressK3sContainerCustomizer()
        );
    }

}
