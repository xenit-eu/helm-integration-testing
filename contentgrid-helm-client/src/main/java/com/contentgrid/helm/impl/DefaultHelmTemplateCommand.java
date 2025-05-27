package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmTemplateCommand;
import com.contentgrid.helm.HelmTemplateCommand.TemplateFlagHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class DefaultHelmTemplateCommand implements HelmTemplateCommand {

    public static final String CMD_TEMPLATE = "template";
    @NonNull
    private final CommandExecutor executor;

    @NonNull
    private final ObjectMapper objectMapper;

    @Override
    public TemplateResult chart(String name, String chart, TemplateFlag... options) {
        return this.exec(name != null ? name : "", chart, List.of(options));
    }

    @SneakyThrows
    private TemplateResult exec(@NonNull String name, @NonNull String chart, @NonNull List<TemplateFlag> flags) {

        List<String> args = new ArrayList<>();

        // name is optional with --generate-name
        if (!name.isEmpty()) {
            args.add(name);
        }

        // chart reference is required
        args.add(chart);

        var handler = new DefaultTemplateFlagHandler(args, objectMapper);

        flags.forEach(flag -> {
            flag.apply(handler);
        });

        var stdout = this.executor.call(CMD_TEMPLATE, args);

        return new TemplateResult(stdout);
    }
}

class DefaultTemplateFlagHandler extends DefaultInstallOptionsHandler implements TemplateFlagHandler {

    public DefaultTemplateFlagHandler(@NonNull List<String> arguments, @NonNull ObjectMapper objectMapper) {
        super(arguments, objectMapper);
    }

    @Override
    public void repository(String repositoryUrl) {
        arguments.add("--repo");
        arguments.add(repositoryUrl);
    }

    @Override
    public void outputDirectory(Path outputDir) {
        arguments.add("--output-dir");
        arguments.add(outputDir.toAbsolutePath().toString());
    }
}
