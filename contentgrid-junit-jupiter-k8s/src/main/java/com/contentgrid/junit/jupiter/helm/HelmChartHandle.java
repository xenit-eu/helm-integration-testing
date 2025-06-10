package com.contentgrid.junit.jupiter.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.contentgrid.helm.Helm;
import com.contentgrid.helm.HelmDependencyCommand.HelmDependency;
import com.contentgrid.helm.HelmInstallCommand.InstallOption;
import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import com.contentgrid.helm.HelmUninstallCommand.UninstallOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * A handle to a helm chart created by the {@link HelmChart @HelmChart} annotation.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class HelmChartHandle implements AutoCloseable {

    @NonNull
    private final Helm helmClient;

    @NonNull
    private final ChartReference chartReference;

    @NonNull
    private final NamespaceHandler namespaceHandler;
    private final boolean addChartRepositories;

    private final Queue<InstallResult> installs = new LinkedList<>();

    /**
     * Install the helm chart
     * @param options Installation options
     * @return The installation result
     */
    public InstallResult install(InstallOption... options) {
        var newOptions = new ArrayList<>(namespaceHandler.installationOptions());
        newOptions.addAll(chartReference.installationOptions());
        newOptions.addAll(List.of(options));

        if(addChartRepositories) {
            chartReference.configureChartRepositories(helmClient);
        }

        var path = chartReference.provisionChart(helmClient);

        var installResult = helmClient.install().chart(path, newOptions.toArray(InstallOption[]::new));
        installs.add(installResult);
        return installResult;
    }

    /**
     * @return Whether this helm chart has already been installed
     */
    public boolean isInstalled() {
        return !installs.isEmpty();
    }

    /**
     * Closes down helm chart, uninstalls all performed installations
     */
    @Override
    public void close() {
        if(installs.isEmpty()) {
            log.warn("Helm chart '{}' was never installed. Don't forget to call HelmChartHandle.install() to install the helm chart.", chartReference);
        }

        while(!installs.isEmpty()) {
            var install = installs.poll();

            helmClient.uninstall().uninstall(install.name(), UninstallOption.namespace(install.namespace()));
        }
    }

    /**
     * Handles helm arguments to install in the namespace that the chart should be installed in
     */
    private sealed interface NamespaceHandler {
        List<InstallOption> installationOptions();
    }

    /**
     * Install the helm chart in a new isolated namespace
     */
    private static final class IsolatedNamespaceHandler implements NamespaceHandler {
        private final String namespace = UUID.randomUUID().toString();

        @Override
        public List<InstallOption> installationOptions() {
            return List.of(
                    InstallOption.namespace(namespace),
                    InstallOption.createNamespace()
            );
        }
    }

    /**
     * Install the helm chart in a specific, existing namespace
     */
    @RequiredArgsConstructor
    private static final class ConfiguredNamespaceHandler implements NamespaceHandler {
        @NonNull
        private final String namespace;

        @Override
        public List<InstallOption> installationOptions() {
            return List.of(InstallOption.namespace(namespace));
        }
    }

    /**
     * Install the helm chart in the default namespace of the current kubernetes context
     */
    private static final class DefaultNamespaceHandler implements NamespaceHandler {

        @Override
        public List<InstallOption> installationOptions() {
            return List.of();
        }
    }

    /**
     * A reference to a helm chart
     */
    private sealed interface ChartReference {

        /**
         * Registers the repositories defined in the referenced helm chart
         * @param helm The helm client to register the repositories against
         */
        void configureChartRepositories(Helm helm);

        /**
         * Installs dependencies and places chart in a location that is accessible by helm
         * @param helm The helm client to install dependencies with and for which the chart should be made accessible
         * @return The plain chart reference that should be passed to helm install
         */
        String provisionChart(Helm helm);

        /**
         * Additional options for helm install
         */
        default List<InstallOption> installationOptions() {
            return List.of();
        }
    }

    /**
     * Reference to a locally available helm chart
     * <p>
     * Local helm charts need to be copied to a temporary location.
     * This is to ensure that:
     *  - charts packaged inside jars can be accessed by helm
     *  - installing dependencies does not modify the referenced helm chart files
     */
    private abstract static sealed class AbstractLocalChartReference implements ChartReference {
        private Path chartTargetDir = null;

        @Override
        public void configureChartRepositories(Helm helm) {
            helm.dependency().list(ensureCopiedToTargetDir())
                    .stream()
                    .map(HelmDependency::repository)
                    // Deduplicate repositories
                    .collect(Collectors.toUnmodifiableSet())
                    .forEach(helm.repository()::add);
        }

        @Override
        @SneakyThrows
        public String provisionChart(Helm helm) {
            var temp = ensureCopiedToTargetDir();

            log.info("Installing dependencies");
            helm.dependency().build(temp);
            return temp.toAbsolutePath().toString();
        }

        private Path ensureCopiedToTargetDir() {
            if(chartTargetDir == null) {
                chartTargetDir = copyToTargetDir();
            }
            return chartTargetDir;
        }

        protected abstract Path copyToTargetDir();

        @SneakyThrows
        protected static Path copyFolder(Path src, Path containingFolder) {
            var targetDir = Files.createTempDirectory(containingFolder, src.getFileName().toString());
            log.info("Copying chart from {} to {}", src.toAbsolutePath(), targetDir.toAbsolutePath());
            try (Stream<Path> stream = Files.walk(src)) {
                stream.forEach(source -> copy(source, targetDir.resolve(src.relativize(source).toString())));
            }
            return targetDir;
        }

        @SneakyThrows
        private static void copy(Path source, Path dest) {
            Files.copy(source, dest, REPLACE_EXISTING);
        }
    }

    /**
     * Reference to a locally available helm chart, in the form of a normal directory on disk
     */
    @RequiredArgsConstructor
    private static final class FileLocalChartReference extends AbstractLocalChartReference {
        @NonNull
        private final Path source;
        @NonNull
        private final Path tempDir;

        @Override
        @SneakyThrows
        protected Path copyToTargetDir() {
            return copyFolder(source, tempDir);
        }

        @Override
        public String toString() {
            return source.toString();
        }
    }

    /**
     * Reference to a locally available helm chart, located inside a jar
     */
    @RequiredArgsConstructor
    private static final class JarChartReference extends AbstractLocalChartReference{
        @NonNull
        private final String uriReference;
        @NonNull
        private final Path tempDir;

        @Override
        @SneakyThrows
        protected Path copyToTargetDir() {
            var separatorPos = uriReference.indexOf('!');
            var jarPath = uriReference.substring(0, separatorPos);

            try(var fs = FileSystems.newFileSystem(Path.of(jarPath), Map.of())) {
                var fullPath = fs.getPath(uriReference.substring(separatorPos+1));
                return copyFolder(fullPath, tempDir);
            }
        }

        @Override
        public String toString() {
            return uriReference;
        }
    }

    /**
     * Reference to a remove OCI chart, optionally with a version
     */
    @RequiredArgsConstructor
    private static final class OciChartReference implements ChartReference {
        @NonNull
        private final String location;
        private final String version;

        @Override
        public void configureChartRepositories(Helm helm) {
            // No repositories to configure for this chart type
        }

        @Override
        public String provisionChart(Helm helm) {
            return location;
        }

        @Override
        public List<InstallOption> installationOptions() {
            if(version != null) {
                return List.of(InstallOption.version(version));
            }
            return List.of();
        }
    }

    /**
     * Parsed location of a helm chart
     * @param type The type of the location (maps to one of the {@link ChartReference} implementations
     * @param location The location of the helm chart, without the type prefix
     */
    private record ParsedChartLocation(LocationType type, String location) {
        @RequiredArgsConstructor
        enum LocationType {
            CLASSPATH("classpath:"),
            JAR("jar:file:"),
            FILE("file:"),
            OCI("oci://");

            private final String protocol;
        }

        /**
         * @return Full helm chart reference, including type prefix
         */
        public String chart() {
            return type().protocol + location();
        }

        /**
         * Parse a helm chart location
         * @param location The location string to parse
         * @return A parsed chart location
         */
        public static ParsedChartLocation parse(String location) {
            for (var locationType : LocationType.values()) {
                if(location.startsWith(locationType.protocol)) {
                    return new ParsedChartLocation(
                            locationType,
                            location.substring(locationType.protocol.length())
                    );
                }
            }
            throw new IllegalArgumentException("Unsupported helm chart '%s'. Supported prefixes are: %s".formatted(
                    location,
                    Arrays.stream(LocationType.values()).map(type -> "'%s'".formatted(type.protocol)).collect(Collectors.joining(", "))
            ));
        }

        /**
         * Instantiates a {@link ChartReference} from a location
         * @param resourceLoader Optionally, the resource loader to use to locate the helm chart (if type is {@link LocationType#CLASSPATH}
         * @param tempDir Optionally, the temporary directory to copy the helm chart to (if it is a local helm chart)
         * @return The {@link ChartReference}
         */
        public ChartReference instantiate(ResourceLoader resourceLoader, Path tempDir) {
            return switch (type()) {
                case CLASSPATH -> {
                    var classpathUrl = resourceLoader.getResource(location());
                    if(classpathUrl == null) {
                        throw new IllegalArgumentException("Chart '%s' not found in %s".formatted(chart(), resourceLoader));
                    }
                    yield ParsedChartLocation.parse(classpathUrl.toExternalForm()).instantiate(resourceLoader, tempDir);
                }
                case JAR -> new JarChartReference(location(), tempDir);
                case FILE -> new FileLocalChartReference(Path.of(location()), tempDir);
                case OCI -> {
                    // A chart reference look like oci://<repo> or oci://<repo>:<version>
                    // We have to separate version from the repo, without taking into account the colon from the protocol
                    var colonPos = chart().indexOf(':', LocationType.OCI.protocol.length());
                    if(colonPos < 0) { // No coo
                        yield new OciChartReference(chart(), null);
                    }
                    yield new OciChartReference(chart().substring(0, colonPos), chart().substring(colonPos+1));
                }
            };
        }
    }

    /**
     * Resolves the location of a helm chart on the classpath
     */
    @FunctionalInterface
    public interface ResourceLoader {
        URL getResource(String name);
    }

    /**
     * Resolves a classpath helm chart from a {@link ClassLoader}
     * <p>
     * The helm chart location is resolved relative to the classpath root
     */
    @RequiredArgsConstructor
    private static final class ClassLoaderResourceLoader implements ResourceLoader {
        @NonNull
        private final ClassLoader classLoader;

        @Override
        public URL getResource(String name) {
            return classLoader.getResource(name);
        }

        @Override
        public String toString() {
            return "ClassLoader %s".formatted(classLoader.getName());
        }
    }

    /**
     * Resolves a classpath helm chart from a {@link Class}
     * <p>
     * The helm chart location is resolved relative to the class (using {@link Class#getResource(String)})
     */
    @RequiredArgsConstructor
    private static final class ClassResourceLoader implements ResourceLoader {
        @NonNull
        private final Class<?> clazz;

        @Override
        public URL getResource(String name) {
            return clazz.getResource(name);
        }

        @Override
        public String toString() {
            return "Package %s".formatted(clazz.getPackageName());
        }
    }

    public static class HelmChartHandleBuilder {
        private Path unpackTempDir;
        private ResourceLoader resourceLoader;

        /**
         * Configures the resource loader to use for resolving <code>classpath:</code> charts
         * @see #resourceClassLoader(ClassLoader)
         * @see #resourceLoaderClass(Class)
         */
        public HelmChartHandleBuilder resourceLoader(ResourceLoader resourceLoader) {
            this.resourceLoader = resourceLoader;
            return this;
        }

        /**
         * Configures the classloader to use to resolve <code>classpath:</code> charts
         */
        public HelmChartHandleBuilder resourceClassLoader(ClassLoader classLoader) {
            return resourceLoader(new ClassLoaderResourceLoader(classLoader));
        }

        /**
         * Configures the class to use to resolve <code>classpath:</code> charts
         */
        public HelmChartHandleBuilder resourceLoaderClass(Class<?> clazz) {
            return resourceLoader(new ClassResourceLoader(clazz));
        }

        /**
         * Configures the temporary directory to unpack local helm charts into
         */
        public HelmChartHandleBuilder unpackTempDir(Path tempDir) {
            this.unpackTempDir = tempDir;
            return this;
        }

        /**
         * Configures the namespace to install the helm chart into as a randomly-generated isolated namespace
         */
        public HelmChartHandleBuilder isolatedNamespace() {
            return namespaceHandler(new IsolatedNamespaceHandler());
        }

        /**
         * Configures the namespace to install the helm chart into
         */
        public HelmChartHandleBuilder namespace(@NonNull String namespace) {
            return namespaceHandler(new ConfiguredNamespaceHandler(namespace));
        }

        /**
         * Configures the namespace to install the the helm chart into as the default namespace of the {@link Helm} client
         */
        public HelmChartHandleBuilder defaultNamespace() {
            return namespaceHandler(new DefaultNamespaceHandler());
        }

        private ResourceLoader getResourceLoader() {
            if(resourceLoader != null) {
                return resourceLoader;
            }

            var contextClassLoader = Thread.currentThread().getContextClassLoader();
            if(contextClassLoader != null) {
                return new ClassLoaderResourceLoader(contextClassLoader);
            }

            return new ClassLoaderResourceLoader(ClassLoader.getSystemClassLoader());
        }

        private HelmChartHandleBuilder chart(ParsedChartLocation parsedChartLocation) {
            return chartReference(
                    parsedChartLocation.instantiate(getResourceLoader(), Objects.requireNonNullElseGet(unpackTempDir, () -> {
                                try {
                                    return Files.createTempDirectory("helm-chart");
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            })
                    ));
        }

        /**
         * Configures the location where the helm chart can be found.
         * <p>
         * Supported locations are:
         * <ul>
         *     <li><code>file:</code> for resolving the chart relative to the current working directory
         *     <li><code>classpath:</code> for resolving the chart from the classpath.
         *     Optionally, use {@link #resourceLoaderClass(Class)} or {@link #resourceClassLoader(ClassLoader)} to configure how the classpath should be resolved.
         *     If none is set, the context classloader for the current thread is used
         *     <li><code>oci://</code> for using OCI charts
         * </ul>
         *
         */
        public HelmChartHandleBuilder chart(String chart) {
            return chart(ParsedChartLocation.parse(chart));
        }

        /**
         * Configures the location where the helm chart can be found.
         * @see #chart(String)
         */
        public HelmChartHandleBuilder chart(URL chart) {
            return chart(chart.toExternalForm());
        }

        /**
         * Configures the parameters set in the {@link HelmChart} annotation
         */
        public HelmChartHandleBuilder fromAnnotation(HelmChart annotation) {
            return chart(annotation.chart())
                    .addChartRepositories(annotation.addChartRepositories())
                    .namespaceHandler(switch (annotation.namespace()) {
                        case HelmChart.NAMESPACE_ISOLATE -> new IsolatedNamespaceHandler();
                        case HelmChart.NAMESPACE_DEFAULT -> new DefaultNamespaceHandler();
                        default -> new ConfiguredNamespaceHandler(annotation.namespace());
                    });
        }
    }
}
