package com.contentgrid.testcontainers.k3s.customizer.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.junit.jupiter.k8s.wait.KubernetesResourceWaiter;
import com.contentgrid.testcontainers.k3s.customizer.AbstractK3sContainerCustomizerTest;
import com.contentgrid.testcontainers.k3s.customizer.ClusterDomainsK3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.cilium.DefaultDenyCiliumK3sContainerCustomizer;
import com.github.dockerjava.api.model.Ports.Binding;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;

class ProxyK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void proxyToInternalService() throws IOException, InterruptedException {
        var container = createContainerOnly(customizers -> {
            customizers.configure(ProxyK3sContainerCustomizer.class);
        });

        var client = createClientFromContainer(container);

        var resources = client.load(getClass().getResourceAsStream("test-ingress.yaml")).serverSideApply();

        new KubernetesResourceWaiter(client)
                .include(resources)
                .await(await -> await.atMost(1, TimeUnit.MINUTES))
                .close();

        var httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(ProxyK3sContainerCustomizer.getProxyAddress(container)))
                .build();

        var response = httpClient.send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://test-ingress.default.svc.cluster.local"))
                .build(), respInfo -> BodySubscribers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Welcome to nginx!");
    }

    @Test
    void proxyDisablesStaticPublicTraefikPort() {
        var container = createContainerOnly(customizers -> {
            customizers.configure(ProxyK3sContainerCustomizer.class);
            customizers.configure(TraefikIngressK3sContainerCustomizer.class);
        });

        assertThat(container.getContainerInfo().getHostConfig().getPortBindings().getBindings())
                .values()
                .flatMap(Arrays::asList)
                .map(Binding.class::cast)
                .allSatisfy(binding -> {
                    // All port bindings are dynamic, no mappings are hardcoded to a fixed port
                    assertThat(binding.getHostPortSpec()).isNullOrEmpty();
                });
    }

    @Test
    void proxyDefaultDeny() throws IOException, InterruptedException {
        var container = createContainerOnly(customizers -> {
            customizers.configure(ProxyK3sContainerCustomizer.class);
            customizers.configure(TraefikIngressK3sContainerCustomizer.class);
            customizers.configure(ClusterDomainsK3sContainerCustomizer.class, dns -> dns.withDomains("ingress.test"));
            customizers.configure(DefaultDenyCiliumK3sContainerCustomizer.class);
        });

        var client = createClientFromContainer(container);

        var resources = client.load(getClass().getResourceAsStream("test-ingress.yaml")).serverSideApply();

        new KubernetesResourceWaiter(client)
                .include(resources)
                .await(await -> await.atMost(1, TimeUnit.MINUTES))
                .close();

        var httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(ProxyK3sContainerCustomizer.getProxyAddress(container)))
                .build();

        var response = httpClient.send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://test-ingress.default.svc.cluster.local"))
                .build(), respInfo -> BodySubscribers.ofString(StandardCharsets.UTF_8));

        // We are not able to access this internal service directly
        // because of the ingress policy on the test-ingress service only allowing the ingress controller (and not the proxy)
        assertThat(response.statusCode()).isEqualTo(500);

        response = httpClient.send(HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://ingress.test"))
                .build(), respInfo -> BodySubscribers.ofString(StandardCharsets.UTF_8));

        // But we can access it through the ingress controller
        // because the ingress policy of the ingress controller allows everyone
        // and the ingress policy of the test-ingress service allows the ingress controller
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Welcome to nginx!");
    }

}