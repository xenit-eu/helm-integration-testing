package com.contentgrid.helm;

import com.contentgrid.helm.HelmInstallCommand.InstallOptionsHandler;
import java.nio.file.Path;
import java.util.Map;
import lombok.Value;

public interface HelmTemplateCommand {

    TemplateResult chart(String name, String chart, TemplateFlag... flags);


    interface TemplateFlag {

        void apply(TemplateFlagHandler handler);

        static TemplateFlag namespace(String namespace) {
            return handler -> handler.namespace(namespace);
        }

        static TemplateFlag values(Map<String, Object> values) {
            return handler -> handler.values(values);
        }

        static TemplateFlag version(String version) {
            return handler -> handler.version(version);
        }

        static TemplateFlag repo(String repositoryUrl) {
            return handler -> handler.repository(repositoryUrl);
        }

        static TemplateFlag outputDir(Path outputDir) {
            return handler -> handler.outputDirectory(outputDir);
        }
    }

    interface TemplateFlagHandler extends InstallOptionsHandler {

        void repository(String repositoryUrl);

        void outputDirectory(Path outputDir);
    }

    @Value
    class TemplateResult {
        final String output;
    }
}

