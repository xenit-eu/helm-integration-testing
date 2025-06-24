package com.contentgrid.testcontainers.k3s.customizer.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.junit.jupiter.k8s.wait.KubernetesResourceWaiter;
import com.contentgrid.junit.jupiter.k8s.wait.ResourceMatcher;
import com.contentgrid.testcontainers.k3s.customizer.AbstractK3sContainerCustomizerTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

class TraefikIngressK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @Test
    void ingressAccessible() throws IOException, InterruptedException {
        var client = createContainer(customizers -> {
            customizers.configure(TraefikIngressK3sContainerCustomizer.class);
        });

        client.apps().deployments().inNamespace("default")
                        .load(new ByteArrayInputStream("""
                                apiVersion: apps/v1
                                kind: Deployment
                                metadata:
                                  name: test-ingress
                                spec:
                                  replicas: 1
                                  selector:
                                    matchLabels:
                                      app: test-ingress
                                  template:
                                    metadata:
                                        labels:
                                            app: test-ingress
                                    spec:
                                        containers:
                                            - name: nginx
                                              image: nginx
                                              ports:
                                                - containerPort: 80
                                                  name: http
                                """.getBytes(StandardCharsets.UTF_8)))
                                .create();

        client.services().inNamespace("default")
                .load(new ByteArrayInputStream("""
                        apiVersion: v1
                        kind: Service
                        metadata:
                          name: test-ingress
                        spec:
                            selector:
                                app: test-ingress
                            ports:
                                - port: 80
                                  targetPort: http
                                  name: http
                        """.getBytes(StandardCharsets.UTF_8)))
                        .create();

        client.network().v1().ingresses()
                .inNamespace("default")
                .load(new ByteArrayInputStream("""
                        apiVersion: networking.k8s.io/v1
                        kind: Ingress
                        metadata:
                            name: test-ingress
                        spec:
                            rules:
                                - host: ingress.test
                                  http:
                                      paths:
                                        - path: /
                                          pathType: Prefix
                                          backend:
                                            service:
                                                name: test-ingress
                                                port:
                                                    name: http
                        """.getBytes(StandardCharsets.UTF_8)))
                .create();

        new KubernetesResourceWaiter(client)
                .deployments(ResourceMatcher.named("test-ingress").inNamespace("default"))
                .await(await -> await.atMost(1, TimeUnit.MINUTES))
                .close();

        var connectionManager = BasicHttpClientConnectionManager.create(
                null,
                new DnsResolver() {
                    @Override
                    public InetAddress[] resolve(final String host) throws UnknownHostException {
                        return InetAddress.getAllByName(DockerClientFactory.instance().dockerHostIpAddress());
                    }

                    @Override
                    public String resolveCanonicalHostname(String host) throws UnknownHostException {
                        return host;
                    }
                },
                RegistryBuilder.<TlsSocketStrategy>create()
                        .build(),
                null
        );

        var httpClient = HttpClientBuilder.create()
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

}