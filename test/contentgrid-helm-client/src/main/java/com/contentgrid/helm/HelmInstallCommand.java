package com.contentgrid.helm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Path;
import java.util.Map;
import lombok.Value;

public interface HelmInstallCommand {


    InstallResult chart(String name, String chart, InstallOption... options);

    /**
     * Install the referenced chart, with --generate-name implied
     *
     * @param chart - a chart reference, a path to a packaged chart, a path to an unpacked chart directory or a URL
     */
    default InstallResult chart(String chart) {
        return this.chart(null, chart, InstallOption.generateName());
    }

    /**
     * Install the chart from the current working directory, with --generate-name implied
     */
    default InstallResult cwd() {
        return this.chart(".");
    }

    interface InstallOption {

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
    }


    interface InstallResult {

        String name();

        String namespace();

        long version();

        Map<String, String> info();

        Map<String, Object> chart();
    }
}




