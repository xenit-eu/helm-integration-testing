package com.contentgrid.junit.jupiter.docker.registry;

import static com.contentgrid.junit.jupiter.docker.registry.DockerRegistryCacheExtension.DOCKERMIRROR_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.engine.execution.NamespaceAwareStore;
import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.DockerClientFactory;

class DockerRegistryCacheExtensionTest {

    @Test
    void evaluateExecutionCondition_enabled() {
        var extension = new DockerRegistryCacheExtension();

        // enabled by default
        assertThat(extension.evaluate(Map.of()).isDisabled()).isFalse();
        assertThat(extension.evaluate(Map.of("CI", "true")).isDisabled()).isFalse();

        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "0")).isDisabled()).isFalse();
        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "false")).isDisabled()).isFalse();
        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "FaLsE")).isDisabled()).isFalse();
    }

    @Test
    void evaluateExecutionCondition_disabled() {
        var extension = new DockerRegistryCacheExtension();
        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "")).isDisabled()).isTrue();
        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "true")).isDisabled()).isTrue();
        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "1")).isDisabled()).isTrue();
        assertThat(extension.evaluate(Map.of("CONTENTGRID_REGISTRY_CACHE_DISABLED", "foobar")).isDisabled()).isTrue();
    }

    @Test
    void beforeAll() throws Exception {
        var extension = new DockerRegistryCacheExtension();
        var context = extensionContext(DockerIoRegistryCacheTest.class);

        // this effectively starts a docker.io/registry:2 container
        extension.beforeAll(context);

        var mirrors = DockerRegistryCacheExtension.getMirrors(context);
        assertThat(mirrors).singleElement().isEqualTo("registry:docker.io");

        var registryEndpoint = DockerRegistryCacheExtension.getMirror(context, mirrors.iterator().next());
        assertThat(registryEndpoint.getName()).isEqualTo("docker.io");

        // assert that this is running somewhere locally
        var dockerHostname = DockerClientFactory.instance().dockerHostIpAddress();
        assertThat(registryEndpoint.getURI().getScheme()).isEqualTo("http");
        assertThat(registryEndpoint.getURI().getHost()).isEqualTo(dockerHostname);

        // check we can access the registry REST API
        var client = WebTestClient.bindToServer()
                .baseUrl(registryEndpoint.getURI().toString())
                .build();

        client.get().uri("/v2/_catalog")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().exists("docker-distribution-api-version");

    }

    @DockerRegistryCache(name = "docker.io", proxy = "https://registry-1.docker.io")
    static class DockerIoRegistryCacheTest {


    }

    static ExtensionContext extensionContext(Class clazz) {
        var valueStore = new NamespacedHierarchicalStore<Namespace>(null);

        ExtensionContext extensionContext = Mockito.mock(ExtensionContext.class);
        Mockito.when(extensionContext.getRequiredTestClass()).thenReturn(clazz);
        Mockito.when(extensionContext.getStore(any())).thenAnswer(
                (Answer<Store>) invocation -> new NamespaceAwareStore(valueStore, invocation.getArgument(0)));

        return extensionContext;
    }
}