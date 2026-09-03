package com.contentgrid.testcontainers.k3s.customizer.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.junit.jupiter.k8s.wait.KubernetesResourceWaiter;
import com.contentgrid.testcontainers.k3s.customizer.AbstractK3sContainerCustomizerTest;
import com.github.dockerjava.api.model.Ports.Binding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.readiness.Readiness;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.awaitility.Awaitility;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

class TraefikIngressK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void ingressAccessible() throws IOException {
        var client = createContainer(customizers -> {
            customizers.configure(TraefikIngressK3sContainerCustomizer.class);
        });

        deployYaml(client, "test-ingress.yaml");


        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new DnsResolver() {
                    @Override
                    public InetAddress[] resolve(final String host) throws UnknownHostException {
                        return InetAddress.getAllByName(DockerClientFactory.instance().dockerHostIpAddress());
                    }

                    @Override
                    public String resolveCanonicalHostname(String host) throws UnknownHostException {
                        return host;
                    }
                })
                .build();

        var httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        assertThat(httpClient.execute(new HttpGet("http://ingress.test/")))
                .satisfies(response -> {
                    assertThat(response.getCode()).isEqualTo(200);
                    assertThat(response.getEntity().getContent().readAllBytes())
                            .contains("Welcome to nginx!".getBytes(StandardCharsets.UTF_8));
                });

        assertThat(httpClient.execute(new HttpGet("http://no-ingress.test/")))
                .satisfies(response -> {
                    assertThat(response.getCode()).isEqualTo(404);
                });
    }

    @Test
    void withUpstreamUpgrade() {
        var container = createContainerOnly(customizers -> {
            customizers.configure(TraefikIngressK3sContainerCustomizer.class);
        });

        var client = createClientFromContainer(container);

        deployYaml(client, "whoami.yaml");

        var httpClient = HttpClient.newBuilder()
                .build();

        // whoami doesn't have a proper readiness probe, so we need to retry a couple of times in case the service is not ready yet
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = httpClient.send(HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost/api"))
                            .header("Host", "whoami.test")
                            .build(), this::whoamiResponse);

                    assertThat(response.statusCode())
                            .withFailMessage("Request failed %d: %s", response.statusCode(), response.body())
                            .isEqualTo(200);
                    // We have succesfully upgrade our frontend protocol to http2
                    assertThat(response.version()).isEqualTo(Version.HTTP_2);
                    assertThat(response.body()).isInstanceOfSatisfying(WhoamiResponse.class, body -> {
                        // And the backend receives an Upgrade header as well
                        assertThat(body.getHeaders()).containsKey("Upgrade");
                    });
                });
    }

    @Test
    void withoutUpstreamUpgrade() {
        var container = createContainerOnly(customizers -> {
            customizers.configure(TraefikIngressK3sContainerCustomizer.class, traefik -> traefik.withoutUpstreamUpgradeHeader());
        });

        var client = createClientFromContainer(container);

        deployYaml(client, "whoami.yaml");

        var httpClient = HttpClient.newBuilder()
                .build();

        // whoami doesn't have a proper readiness probe, so we need to retry a couple of times in case the service is not ready yet
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = httpClient.send(HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost/api"))
                            .header("Host", "whoami.test")
                            .build(), this::whoamiResponse);

                    assertThat(response.statusCode())
                            .withFailMessage("Request failed %d: %s", response.statusCode(), response.body())
                            .isEqualTo(200);
                    // We have succesfully upgrade our frontend protocol to http2
                    assertThat(response.version()).isEqualTo(Version.HTTP_2);
                    assertThat(response.body()).isInstanceOfSatisfying(WhoamiResponse.class, body -> {
                        // But the backend does not receive a lone upgrade header
                        assertThat(body.getHeaders()).doesNotContainKey("Upgrade");
                    });
                });
    }

    @Test
    void withoutExposedPort() {
        var container = createContainerOnly(customizers -> {
            customizers.configure(TraefikIngressK3sContainerCustomizer.class, traefik -> traefik.withoutExposedPort());
        });

        assertThat(container.getContainerInfo().getHostConfig().getPortBindings().getBindings())
                .values()
                .flatMap(Arrays::asList)
                .map(Binding.class::cast)
                .allSatisfy(binding -> {
                    // All port bindings are dynamic, no mappings are hardcoded to a fixed port
                    assertThat(binding.getHostPortSpec()).isNullOrEmpty();
                });

        var client = createClientFromContainer(container);

        // Container should only report ready once the traefik pod is ready
        assertThat(client.pods().inNamespace("kube-system").withLabel("app.kubernetes.io/name", "traefik").list().getItems())
                .singleElement()
                .matches(Readiness::isPodReady);

        deployYaml(client, "whoami.yaml");

        var pf = client.pods()
                .inNamespace("kube-system")
                .withLabel("app.kubernetes.io/name", "traefik")
                .resources()
                .findFirst()
                .orElseThrow()
                .portForward(8000);

        var httpClient = HttpClient.newBuilder()
                .build();

        // whoami doesn't have a proper readiness probe, so we need to retry a couple of times in case the service is not ready yet
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var response = httpClient.send(HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + pf.getLocalPort() + "/api"))
                            .header("Host", "whoami.test")
                            .build(), this::whoamiResponse);
                    assertThat(response.statusCode())
                            .withFailMessage("Request failed %d: %s", response.statusCode(), response.body())
                            .isEqualTo(200);
                });
    }

    private BodySubscriber<?> whoamiResponse(ResponseInfo responseInfo) {
        if (responseInfo.statusCode() == 200) {
            return BodySubscribers.mapping(BodySubscribers.ofInputStream(), WhoamiResponse::read);
        } else {
            return BodySubscribers.ofString(StandardCharsets.UTF_8);
        }
    }

    private void deployYaml(KubernetesClient client, String resource) {
        var resources = client.load(getClass().getResourceAsStream(resource)).serverSideApply();

        new KubernetesResourceWaiter(client)
                .include(resources)
                .await(await -> await.atMost(1, TimeUnit.MINUTES))
                .close();
    }

    @Test
    void mapConversion() {
        var tree = TraefikIngressK3sContainerCustomizer.convertToTree(Map.of(
                "x.y", 1,
                "x.z", 2,
                "x.x", List.of("abc", "def"),
                "y", true
        ));

        assertThat(tree).isEqualTo(Map.of(
                "x", Map.of(
                        "y", 1,
                        "z", 2,
                        "x", List.of("abc", "def")
                ),
                "y", true
        ));

        assertThatThrownBy(() -> TraefikIngressK3sContainerCustomizer.convertToTree(Map.of(
                "x.y", true,
                "x.y.z", 1
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("conflict at x.y");
    }

}
