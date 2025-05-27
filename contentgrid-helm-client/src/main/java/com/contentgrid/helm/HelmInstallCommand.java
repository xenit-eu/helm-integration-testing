package com.contentgrid.helm;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public interface HelmInstallCommand {


    /**
     * Install helm chart referenced by a local path to a packaged or unpacked chart.
     *
     * @param name of the helm release
     * @param chart a chart reference, a path to a packaged chart, a path to an unpacked chart directory or a URL
     * @param options install flags
     */
    InstallResult chart(String name, String chart, InstallOption... options);

    /**
     * Install helm chart referenced by a local path to a packaged or unpacked chart.
     *
     * @param name of the helm release
     * @param chartPath path to the chart
     * @param options install flags
     */
    default InstallResult chart(String name, Path chartPath, InstallOption... options) {
        return this.chart(name, chartPath.toAbsolutePath().normalize().toString(), options);
    }

    /**
     * Install the referenced chart, with --generate-name implied
     *
     * @param chart a chart reference, a path to a packaged chart, a path to an unpacked chart directory or a URL
     * @param options additional installation options
     */
    default InstallResult chart(String chart, InstallOption... options) {
        var installOptions = Arrays.copyOf(options, options.length+1);
        installOptions[options.length] = InstallOption.generateName();
        return this.chart(null, chart, installOptions);
    }

    /**
     * Install the chart from the current working directory, with --generate-name implied
     *
     * @deprecated This confusingly-named function can be replaced by {@code .chart(".")}
     */
    @Deprecated(since = "0.0.8", forRemoval = true)
    default InstallResult cwd() {
        return this.chart(".");
    }

    interface InstallOption {

        /**
         * Untyped arguments appended to the command. Main use case is adding less-common flags.
         */
        static InstallOption arguments(String... args) {
            return handler -> handler.arguments(args);
        }

        /**
         * Simulate an install, implying '--dry-run=client', no cluster connections will be attempted.
         */
        static InstallOption dryRun() {
            return handler -> handler.dryRun();
        }

        void apply(InstallOptionsHandler handler);

        /**
         * Kubernetes namespace scope for this request
         * @param namespace kubernetes namespace
         */
        static InstallOption namespace(String namespace) {
            return handler -> handler.namespace(namespace);
        }

        /**
         * Create the release namespace if not present
         */
        static InstallOption createNamespace() {
            return InstallOptionsHandler::createNamespace;
        }

        /**
         * Generate the name (and omit the NAME parameter)
         */
        static InstallOption generateName() {
            return InstallOptionsHandler::generateName;
        }

        /**
         * Set values from a values file
         * @param file is values.yml
         */
        static InstallOption values(Path file) {
            return handler -> handler.values(file);
        }

        /**
         * Set JSON values
         * @param values map with values
         */
        static InstallOption values(Map<String, Object> values) {
            return handler -> handler.values(values);
        }

        /**
         * Specify a version constraint for the chart version to use.
         * This constraint can be a specific tag (e.g. 1.1.1) or it may reference a valid range (e.g. ^2.0.0). If this
         * is not specified, the latest version is used.
         * @param version chart version constraint
         */
        static InstallOption version(String version) {
            return handler -> handler.version(version);
        }

        /**
         * Time to wait for any individual Kubernetes operation. (default "5m0s")
         * @param duration timeout expression
         */
        static InstallOption timeout(String duration) {
            return handler -> handler.timeout(duration);
        }

    }

    interface InstallOptionsHandler {
        void namespace(String namespace);
        void createNamespace();
        void generateName();
        void values(Path file);
        void values(Map<String, Object> values);
        void version(String version);
        void timeout(String duration);
        void dryRun();
        void arguments(String ... args);
    }


    interface InstallResult {

        String name();

        String namespace();

        long version();

        Map<String, String> info();

        Map<String, Object> chart();
    }
}




