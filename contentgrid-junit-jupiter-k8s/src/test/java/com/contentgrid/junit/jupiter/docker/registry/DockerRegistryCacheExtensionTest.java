package com.contentgrid.junit.jupiter.docker.registry;

import static com.contentgrid.junit.jupiter.docker.registry.DockerRegistryCacheExtension.CONTENTGRID_REGISTRY_CACHE_DISABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.util.Map;
import java.util.Optional;
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

    static final String CONFIG_KEY = "contentgrid.registryCache.disabled";
    @Test
    void evaluateExecutionCondition_enabled() {
        var extension = new DockerRegistryCacheExtension();
        var context =  Mockito.mock(ExtensionContext.class);

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.empty());
        assertThat(extension.isExtensionEnabled(context)).isTrue();

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of("0"));
        assertThat(extension.isExtensionEnabled(context)).isTrue();

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of("false"));
        assertThat(extension.isExtensionEnabled(context)).isTrue();

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of("FaLsE"));
        assertThat(extension.isExtensionEnabled(context)).isTrue();
    }

    @Test
    void evaluateExecutionCondition_disabled() {
        var extension = new DockerRegistryCacheExtension();
        var context =  Mockito.mock(ExtensionContext.class);

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of(""));
        assertThat(extension.isExtensionEnabled(context)).isFalse();

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of("true"));
        assertThat(extension.isExtensionEnabled(context)).isFalse();

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of("1"));
        assertThat(extension.isExtensionEnabled(context)).isFalse();

        Mockito.when(context.getConfigurationParameter(CONFIG_KEY)).thenReturn(Optional.of("foobar"));
        assertThat(extension.isExtensionEnabled(context)).isFalse();
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