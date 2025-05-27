package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmUninstallCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class DefaultHelmUninstallCommand implements HelmUninstallCommand {

    private static final String CMD_UNINSTALL = "uninstall";

    @NonNull
    private final CommandExecutor executor;

    @Override
    @SneakyThrows
    public UninstallResult uninstall(@NonNull String name, UninstallOption... options) {
        List<String> args = new ArrayList<>();
        args.add(name);

        var handler = new DefaultUninstallOptionsHandler(args);
        for (var option : options) {
            option.apply(handler);
        }

        executor.call(CMD_UNINSTALL, args);

        return new UninstallResult() {
        };
    }

    @RequiredArgsConstructor
    private static class DefaultUninstallOptionsHandler implements UninstallOptionsHandler {
        @NonNull
        protected final List<String> arguments;

        @Override
        public void namespace(String namespace) {
            arguments.add("--namespace");
            arguments.add(namespace);
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
}
