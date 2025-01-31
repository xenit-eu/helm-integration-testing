package com.contentgrid.junit.jupiter.helm;

import com.contentgrid.helm.Helm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.platform.commons.util.StringUtils;

public interface HasHelmClient {

    default Helm getHelmClient(ExtensionContext context) {
        return getStore(context).getOrComputeIfAbsent(Helm.class, key -> {

            var builder = Helm.builder();

            // setup working directory
            var workingDir = workingDirectory(context);
            builder = builder.withWorkingDirectory(workingDir);

            // load kubeconfig from system properties or env var
            var kubeconfig = getKubeconfig();
            if (kubeconfig.isPresent()) {
                builder = builder.withKubeConfig(kubeconfig.get());
            }

            try {
                // configure helm with private repository config location
                builder = builder.withRepositoryConfig(Files.createTempFile("helm-repo-", "-config"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            var fabric8Namespace = getFabric8Namespace(context);
            if (fabric8Namespace.isPresent()) {
                builder = builder.withNamespace(fabric8Namespace.get().getMetadata().getName());
            }

            return builder.build();
        }, Helm.class);
    }

    private static Optional<io.fabric8.kubernetes.api.model.Namespace> getFabric8Namespace(ExtensionContext context) {
        // try to load fabric8 NamespaceExtension
        var FABRIC8_NAMESPACE = io.fabric8.kubernetes.api.model.Namespace.class;
        var store = context.getRoot().getStore(Namespace.create(context.getRequiredTestClass()));
        var k8sNamespace = store.get(FABRIC8_NAMESPACE, FABRIC8_NAMESPACE);
        return Optional.ofNullable(k8sNamespace);
    }

    private static Optional<Path> getKubeconfig() {
        String answer = System.getProperty("kubeconfig");
        if (StringUtils.isNotBlank(answer)) {
            return Optional.of(Path.of(answer));
        }

        answer = System.getenv("KUBECONFIG");
        if (StringUtils.isNotBlank((answer))) {
            return Optional.of(Path.of(answer));
        }

        return Optional.empty();
    }

    default Path workingDirectory(ExtensionContext context) {
        return getStore(context).getOrComputeIfAbsent("working-directory", key -> {
            try {
                return Files.createTempDirectory("helm-");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Path.class);
    }

    private static ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(HelmClient.class));
    }
}
