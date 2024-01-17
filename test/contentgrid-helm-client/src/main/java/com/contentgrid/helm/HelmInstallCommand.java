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

        static InstallOption namespace(String namespace) {
            return handler -> handler.namespace(namespace);
        }

        static InstallOption createNamespace() {
            return InstallOptionsHandler::createNamespace;
        }

        static InstallOption generateName() {
            return InstallOptionsHandler::generateName;
        }

        static InstallOption values(Path file) {
            return handler -> handler.values(file);
        }

        static InstallOption values(Map<String, Object> values) {
            return handler -> handler.values(values);
        }

        static InstallOption version(String version) {
            return handler -> handler.version(version);
        }

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


    @Value
    @JsonIgnoreProperties(ignoreUnknown = true)
    class DefaultInstallResult implements InstallResult {

        String name;
        String namespace;
        long version;
        Map<String, String> info;
        Map<String, Object> chart;
    }

    interface InstallResult {

        String getName();

        String getNamespace();

        long getVersion();

        Map<String, String> getInfo();

        Map<String, Object> getChart();
    }
}




