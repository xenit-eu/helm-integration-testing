package com.contentgrid.testcontainers.k3s.customizer.ingress;

import com.contentgrid.testcontainers.k3s.customizer.CustomizerUtils;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import com.contentgrid.testcontainers.k3s.customizer.WaitStrategyCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.cilium.CiliumK3sContainerCustomizer;
import java.net.InetSocketAddress;
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
                // The nodePort itself can not be waited on: kube-proxy does not open a listening socket for it,
                // and the proxy does not answer to non-proxied HTTP requests
                Wait.forSuccessfulCommand("kubectl wait pod --namespace kube-system --selector helm-integration-testing.contentgrid.com/app=proxy --for=condition=ready --timeout=0")
                        .withStartupTimeout(CiliumK3sContainerCustomizer.TIMEOUT)
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
                CustomizerUtils.forClassResource(ProxyK3sContainerCustomizer.class, "proxy.yaml"),
                "/var/lib/rancher/k3s/server/manifests/proxy.yaml"
        );
        // The kubernetes NetworkPolicy in proxy.yaml can not express egress to the node itself,
        // which is required to reach services that are exposed on the node (like the ingress controller)
        // This is a separate file, because it may fail to apply when Cilium is not installed,
        // but a failure to apply this file will not result in the rest of the application failing to start
        container.withCopyToContainer(
                CustomizerUtils.forClassResource(ProxyK3sContainerCustomizer.class, "proxy-cilium.yaml"),
                "/var/lib/rancher/k3s/server/manifests/proxy-cilium.yaml"
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
