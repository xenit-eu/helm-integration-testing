package com.contentgrid.junit.jupiter.helm;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.contentgrid.helm.Helm;
import com.contentgrid.helm.HelmDependencyCommand.HelmDependency;
import com.contentgrid.helm.HelmInstallCommand.InstallOption;
import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import com.contentgrid.helm.HelmUninstallCommand.UninstallOption;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * A handle to a helm chart created by the {@link HelmChart @HelmChart} annotation.
 * <p>
 *
 *
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class HelmChartHandle implements AutoCloseable {
    private final Helm client;
    private final HelmChart configuration;
    private final ClassLoader classLoader;
    private final Path tempDir;

    private final Queue<InstallResult> installs = new LinkedList<>();

    public InstallResult install(InstallOption... options) {
        var newOptions = createAdditionalOptions();
        newOptions.addAll(List.of(options));

        var chartReference = getChartReference();

        if(configuration.addChartRepositories()) {
            chartReference.configureChartRepositories(client);
        }

        var path = chartReference.provisionChart(client);

        var installResult = client.install().chart(path, newOptions.toArray(InstallOption[]::new));
        installs.add(installResult);
        return installResult;
    }

    private ChartReference getChartReference() {
        var chart = configuration.chart();
        var parsedLocation = ParsedChartLocation.parse(configuration.chart());

        Optional<Path> localPath = switch (parsedLocation.type()) {
            case CLASSPATH -> {
                var classpathUrl = classLoader.getResource(parsedLocation.path());
                if(classpathUrl == null) {
                    throw new IllegalArgumentException("Chart '%s' not found".formatted(chart));
                }
                try {
                    yield Optional.of(Path.of(classpathUrl.toURI()));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            case FILE -> Optional.of(Path.of(parsedLocation.path()));
            default -> Optional.empty();
        };

        return localPath.map(HelmChartHandle::chartBasePath)
                .<ChartReference>map(path -> new LocalChartReference(path, tempDir))
                .orElseGet(() -> new RemoteChartReference(parsedLocation.path()));
    }

    private List<InstallOption> createAdditionalOptions() {
        List<InstallOption> options = new ArrayList<>();

        options.addAll(switch (configuration.namespace()) {
            case HelmChart.NAMESPACE_DEFAULT -> List.of();
            case HelmChart.NAMESPACE_ISOLATE -> {
                var namespace = UUID.randomUUID();
                yield List.of(
                        InstallOption.namespace(namespace.toString()),
                        InstallOption.createNamespace()
                );
            }
            default -> List.of(InstallOption.namespace(configuration.namespace()));
        });

        return options;
    }

    @Override
    public void close() {
        if(installs.isEmpty()) {
            log.warn("Helm chart '{}' was never installed. Don't forget to call HelmChartHandle.install() to install the helm chart.", configuration.chart());
        }

        while(!installs.isEmpty()) {
            var install = installs.poll();

            client.uninstall().uninstall(install.name(), UninstallOption.namespace(install.namespace()));
        }
    }

    private sealed interface ChartReference {
        void configureChartRepositories(Helm helm);
        String provisionChart(Helm helm);
    }

    @RequiredArgsConstructor
    private static final class LocalChartReference implements ChartReference {
        private final Path path;
        private final Path tempDir;

        @Override
        public void configureChartRepositories(Helm helm) {
            helm.dependency().list(path)
                    .stream()
                    .map(HelmDependency::repository)
                    // Deduplicate repositories
                    .collect(Collectors.toUnmodifiableSet())
                    .forEach(helm.repository()::add);
        }

        @Override
        @SneakyThrows
        public String provisionChart(Helm helm) {
            var temp = Files.createTempDirectory(tempDir, path.getFileName().toString());
            copyFolder(path, temp);

            log.info("Installing dependencies");
            helm.dependency().build(temp);
            return temp.toAbsolutePath().toString();
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

    @RequiredArgsConstructor
    private final class RemoteChartReference implements ChartReference {
        private final String location;

        @Override
        public void configureChartRepositories(Helm helm) {

        }

        @Override
        public String provisionChart(Helm helm) {
            return location;
        }
    }

    private static Path chartBasePath(Path location) {
        if (Files.isDirectory(location) && Files.exists(location.resolve("Chart.yaml"))) {
            return location;
        } else if (Files.isRegularFile(location) && location.getFileName().toString()
                .equals("Chart.yaml")) {
            return location.getParent();
        }
        throw new IllegalArgumentException("No 'Chart.yaml' found at '%s'".formatted(location));
    }


    private record ParsedChartLocation(LocationType type, String path) {
        enum LocationType {
            CLASSPATH,
            FILE,
            OTHER
        }

        public static ParsedChartLocation parse(String location) {
            var firstColon = location.indexOf(':');
            var prefix = location.substring(0, firstColon);

            var locationType = switch(prefix) {
                case "classpath" -> LocationType.CLASSPATH;
                case "file" -> LocationType.FILE;
                default -> LocationType.OTHER;
            };

            if(locationType != LocationType.OTHER) {
                location = location.substring(firstColon+1);
            }

            return new ParsedChartLocation(locationType, location);
        }
    }

}
