package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmDependencyCommand;
import com.contentgrid.helm.HelmDependencyCommand.HelmDependency;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class DefaultHelmDependencyCommand implements HelmDependencyCommand {

    private static final String CMD_DEPENDENCY = "dependency";

    @NonNull
    private final CommandExecutor executor;

    @Override
    @SneakyThrows
    public List<HelmDependency> list() {
        return Arrays.stream(this.executor.call(CMD_DEPENDENCY, "list").split(System.lineSeparator()))
                .skip(1) // skip header
                .filter(Objects::nonNull)
                .map(line -> line.split("\\s+"))
                .filter(parts -> parts.length == 4)
                .map(parts -> createHelmDependency(parts[0], parts[1], parts[2], parts[3]))
                .toList();
    }

    @Override
    @SneakyThrows
    public void build(@NonNull String chart) {
        this.executor.call(CMD_DEPENDENCY, "build", chart);
    }

    private static HelmDependency createHelmDependency(String name, String version, String repository, String status) {
        return new DefaultHelmDependency(name, version, URI.create(repository), status);
    }

}

record DefaultHelmDependency(String name, String version, URI repository, String status) implements HelmDependency {

}
