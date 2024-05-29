package com.contentgrid.junit.jupiter.docker.registry;

import com.contentgrid.testcontainers.registry.DistributionRegistryContainer;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Bind;
import java.io.File;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.platform.commons.support.AnnotationSupport;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.RegistryAuthLocator;

public class DockerRegistryCacheExtension implements BeforeAllCallback {

    public static final Namespace DOCKERMIRROR_NAMESPACE = Namespace.create(DockerRegistryCacheExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var annotations = AnnotationSupport
                .findRepeatableAnnotations(context.getRequiredTestClass(), DockerRegistryCache.class);

        if (annotations.isEmpty()) {
            var message = "@%s not found".formatted(DockerRegistryCache.class.getSimpleName());
            throw new ExtensionConfigurationException(message);
        }

        var store = context.getStore(DOCKERMIRROR_NAMESPACE);

        // create a distribution-registry-mirror for each annotation
        annotations.forEach(annotation -> {

            // create and start registry mirror container
            var mirror = (DockerRegistryCacheContainer) store.getOrComputeIfAbsent(
                    "registry:%s".formatted(annotation.name()),
                    mirrorName -> this.createContainer(annotation).start()
            );

            // maintain a list of mirrors
            var mirrors = (Set<String>) store.getOrComputeIfAbsent("mirrors", (key) -> new HashSet<>());
            mirrors.add(mirror.getKey());
        });
    }

    private DockerRegistryCacheContainer createContainer(@NonNull DockerRegistryCache annotation) {

        var hostPathPrefix = annotation.hostPath();
        if (!hostPathPrefix.isBlank() && !hostPathPrefix.endsWith(File.pathSeparator)) {
            hostPathPrefix += File.pathSeparator;
        }

        var registryStorageHostPath = hostPathPrefix + "docker-registry-%s".formatted(annotation.name());
        var container = new DistributionRegistryContainer()
                .withCreateContainerCmdModifier(cmd ->
                {
                    // optionally rename the container
                    // cmd.withName("registry-%s".formatted(annotation.name()));

                    // mount paths
                    Objects.requireNonNull(cmd.getHostConfig())
                            .withBinds(Bind.parse(registryStorageHostPath + ":/var/lib/registry:rw"));
                });

        if (!annotation.proxy().isBlank()) {
            // lookup potential registry credentials
            var authConfig = RegistryAuthLocator.instance()
                    .lookupAuthConfig(DockerImageName.parse("%s/".formatted(annotation.name())), new AuthConfig());

            container = container.withProxy(annotation.proxy(), authConfig.getUsername(), authConfig.getPassword());
        }

        return new DockerRegistryCacheContainer(annotation.name(), container);
    }


    /**
     * An adapter for Testcontainers {@link Startable} that implement {@link CloseableResource}, thereby letting the
     * JUnit automatically stop containers once the current {@link ExtensionContext} is closed.
     */
    private static class DockerRegistryCacheContainer implements CloseableResource,
            DockerRegistryEndpoint {

        @Getter
        @NonNull
        private String name;

        @NonNull
        private DistributionRegistryContainer container;

        private DockerRegistryCacheContainer(String registryName, DistributionRegistryContainer container) {
            this.name = registryName;
            this.container = container;
        }

        private DockerRegistryCacheContainer start() {
            container.start();
            return this;
        }

        public String getKey() {
            return "registry:%s".formatted(name);
        }

        @Override
        public URI getURI() {
            return URI.create("http://" + this.container.getRegistry());
        }

        @Override
        public void close() {
            container.stop();
        }
    }
}
