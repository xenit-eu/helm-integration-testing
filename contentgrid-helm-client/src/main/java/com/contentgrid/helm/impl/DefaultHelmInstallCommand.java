package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmInstallCommand;
import com.contentgrid.helm.HelmInstallCommand.InstallOptionsHandler;
import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class DefaultHelmInstallCommand implements HelmInstallCommand {

    private static final String CMD_INSTALL = "install";

    @NonNull
    private final CommandExecutor executor;

    @NonNull
    private final ObjectMapper objectMapper;

    @Override
    public InstallResult chart(String name, String chart, InstallOption... options) {
        return this.install(name != null ? name : "", chart, List.of(options));
    }

    @SneakyThrows
    private InstallResult install(@NonNull String name, @NonNull String chart, @NonNull List<InstallOption> options) {

        List<String> args = new ArrayList<>();

        // name is optional with --generate-name
        if (!name.isEmpty()) {
            args.add(name);
        }

        // chart reference is required
        args.add(chart);

        var handler = new DefaultInstallOptionsHandler(args, this.objectMapper);
        for (InstallOption option : options) {
            option.apply(handler);
        }

        // using json output to parse result
        args.addAll(List.of("--output", "json"));

        var stdout = this.executor.call(CMD_INSTALL, args);

        log.info(stdout);

        return this.objectMapper.readValue(stdout, DefaultInstallResult.class);
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
record DefaultInstallResult(String name, String namespace, long version, Map<String, String> info,
                            Map<String, Object> chart) implements InstallResult {

}

@RequiredArgsConstructor
class DefaultInstallOptionsHandler implements InstallOptionsHandler {

    @NonNull
    protected final List<String> arguments;

    @NonNull
    protected final ObjectMapper objectMapper;

    @Override
    public void namespace(String namespace) {
        arguments.add("--namespace");
        arguments.add(namespace);
    }

    @Override
    public void createNamespace() {
        arguments.add("--create-namespace");
    }

    @Override
    public void generateName() {
        arguments.add("--generate-name");
    }

    @Override
    public void values(Path path) {
        arguments.add("--values");
        arguments.add(path.toAbsolutePath().toString());
    }

    @SneakyThrows
    @Override
    public void values(Map<String, Object> values) {
        for (Entry<String, Object> entry : values.entrySet()) {
            arguments.add("--set-json");
            arguments.add(entry.getKey() + "=" + objectMapper.writeValueAsString(entry.getValue()));
        }
    }

    @Override
    public void version(String version) {
        arguments.add("--version");
        arguments.add(version);
    }

    @Override
    public void timeout(String duration) {
        arguments.add("--timeout");
        arguments.add(duration);
    }

    @Override
    public void dryRun() {
        arguments.add("--dry-run");
    }

    @Override
    public void arguments(String... args) {
        arguments.addAll(Arrays.asList(args));
    }
}
