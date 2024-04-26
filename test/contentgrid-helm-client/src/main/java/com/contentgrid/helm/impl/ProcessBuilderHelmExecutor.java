package com.contentgrid.helm.impl;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ProcessBuilderHelmExecutor implements CommandExecutor {

    @Getter
    @Accessors(fluent = true)
    private final Map<String, String> environment;

    private final File workingDirectory;

    public ProcessBuilderHelmExecutor(Map<String, String> environment, File workingDirectory) {
        this.environment = Map.copyOf(environment);
        this.workingDirectory = workingDirectory;
    }

    @Override
    public Process exec(String command, List<String> args) {

        ProcessBuilder builder = new ProcessBuilder();
        builder.command("helm");
        builder.command().add(command);
        builder.command().addAll(args);

        builder.environment().putAll(this.environment);
        builder.directory(this.workingDirectory);

        log.info("$ {} {}",
                this.environment.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(" ")),
                String.join(" ", builder.command())
        );

        try {
            return builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
