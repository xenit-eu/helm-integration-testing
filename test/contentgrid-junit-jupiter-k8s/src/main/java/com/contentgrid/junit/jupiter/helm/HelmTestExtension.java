package com.contentgrid.junit.jupiter.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.contentgrid.helm.Helm;
import com.contentgrid.helm.impl.DefaultHelmBuilder;
import com.contentgrid.helm.HelmDependencyCommand.HelmDependency;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestClusterExtension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.StringUtils;

@Slf4j
public class HelmTestExtension implements HasHelmClient, BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        // copy chart to working directory
        var workingDirectory = workingDirectory(context);
        AnnotationSupport.findAnnotation(context.getRequiredTestClass(), HelmTest.class)
                .map(HelmTest::chart)
                .flatMap(chart -> findChartPath(context.getRequiredTestClass(), chart))
                .ifPresent(chartLocation -> copyFolder(chartLocation, workingDirectory));

        // get all the repositories that should be installed
        // if - and only if - there is a `Chart.yaml` available, auto-import repositories
        if (Files.exists(workingDirectory.resolve("Chart.yaml"))) {
            AnnotationSupport.findAnnotation(context.getRequiredTestClass(), HelmTest.class)
                    .filter(HelmTest::addChartRepositories)
                    .ifPresent(helmTest -> {
                        log.debug("working directory: {}", workingDirectory.toAbsolutePath());
                        var helm = getHelmClient(context);
                        helm.dependency().list().stream().map(HelmDependency::repository)
                                .forEach(repo -> helm.repository().add(repo));
                    });
        }
    }

    static Optional<Path> findChartPath(@NonNull Class<?> testClass, @NonNull String chart) {
        if (chart.isEmpty()) {
            return Optional.empty();
        }

        if (chart.startsWith("classpath:")) {
            // classpath resource
            chart = chart.substring("classpath:".length());
            try {
                var classpathUrl = testClass.getClassLoader().getResource(chart);
                if (classpathUrl != null) {
                     var location = Path.of(classpathUrl.toURI());
                    if (Files.isDirectory(location) && Files.exists(location.resolve("Chart.yaml"))) {
                        return Optional.of(location);
                    } else if (Files.isRegularFile(location) && location.getFileName().toString().equals("Chart.yaml")) {
                        return Optional.of(location.getParent());
                    }
                }
            } catch (URISyntaxException e) {
                // skip
            }
        } else {
            // host path
            var location = Path.of(chart);
            if (Files.isDirectory(location) && Files.exists(location.resolve("Chart.yaml"))) {
                return Optional.of(location);
            } else if (Files.isRegularFile(location) && location.getFileName().toString().equals("Chart.yaml")) {
                return Optional.of(location.getParent());
            }
        }

        // fall through
        throw new ExtensionConfigurationException("Chart '%s' not found".formatted(chart));
    }

    @SneakyThrows
    private static void copyFolder(Path src, Path dest) {
        log.info("Copying chart from {} to {}", src.toAbsolutePath(), dest.toAbsolutePath());
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }
    }

    @SneakyThrows
    private static void copy(Path source, Path dest) {
        Files.copy(source, dest, REPLACE_EXISTING);
    }

}
