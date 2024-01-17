package com.contentgrid.helm.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface CommandExecutor {

    /**
     * Readonly view of the environment variables
     *
     * @return env vars
     */
    Map<String, String> environment();

    Process exec(String command, List<String> args);

    default String call(String command, String ... args) throws CommandException {
        return call(command, List.of(args));
    }

    default String call(String command, List<String> args) throws CommandException {

        var process = this.exec(command, args);

        try (var reader = process.inputReader()) {

            var stdout = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new CommandException(exitCode, stderr);
            }

            return stdout;
        } catch (InterruptedException e) {
            // process was killed
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class CommandException extends Exception {

        private final long exitCode;

        public CommandException(long exitCode, String reason) {
            super(reason);
            this.exitCode = exitCode;
        }
    }
}
