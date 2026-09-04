package com.contentgrid.testcontainers.k3s.customizer.ingress;

import com.contentgrid.testcontainers.k3s.customizer.CustomizerUtils;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import com.contentgrid.testcontainers.k3s.customizer.WaitStrategyCustomizer;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.k3s.K3sContainer;

/**
 * Install a HTTP proxy inside the cluster, so it can connect to cluster-internal services
 * <p>
 * When combined with {@link TraefikIngressK3sContainerCustomizer} and {@link com.contentgrid.testcontainers.k3s.customizer.ClusterDomainsK3sContainerCustomizer},
 * it allows connecting to the configured cluster domains via the proxy.
 */
public class ProxyK3sContainerCustomizer implements K3sContainerCustomizer {

    public static final int PROXY_NODE_PORT = 30100;

    @Override
    public void onRegister(K3sContainerCustomizers customizers) {
        customizers.configure(WaitStrategyCustomizer.class, wait -> wait.withAdditionalWaitStrategy(
                getClass(),
                Wait.forListeningPorts(PROXY_NODE_PORT)
                        .withStartupTimeout(Duration.ofMinutes(2))
        ));
        customizers.maybeConfigure(
                TraefikIngressK3sContainerCustomizer.class,
                TraefikIngressK3sContainerCustomizer::withoutExposedPort
        );
    }

    @Override
    public void customize(K3sContainer container) {
        container.addExposedPort(PROXY_NODE_PORT);
        container.withCopyToContainer(
                CustomizerUtils.forClassResource(ProxyK3sContainerCustomizer.class, "tinyproxy.yaml"),
                "/var/lib/rancher/k3s/server/manifests/tinyproxy.yaml"
        );
    }

    /**
     * Obtain the proxy address from the k3s container
     * @return The address and port to connect to to reach the proxy
     */
    public static InetSocketAddress getProxyAddress(K3sContainer container) {
        var proxyHost = container.getHost();
        var proxyPort = container.getMappedPort(PROXY_NODE_PORT);
        return new InetSocketAddress(proxyHost, proxyPort);
    }
}
