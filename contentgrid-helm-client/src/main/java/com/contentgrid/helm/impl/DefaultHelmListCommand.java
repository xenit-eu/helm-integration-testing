package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmListCommand;
import com.contentgrid.helm.HelmListCommand.HelmRelease;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultHelmListCommand implements HelmListCommand {

    public static final TypeReference<List<DefaultHelmRelease>> RELEASES_TYPEREF = new TypeReference<>() {
    };
    @NonNull
    private final CommandExecutor executor;

    @NonNull
    private final ObjectMapper objectMapper;

    public static final String CMD_LIST = "list";

    // magic Go constant for RFC3339 - don't touch
    private static final String RFC3339 = "2006-01-02T15:04:05Z07:00";

    @SneakyThrows
    @Override
    public List<HelmRelease> releases(ListOption... options) {
        List<String> args = new ArrayList<>();
        args.addAll(List.of("--output", "json"));
        args.addAll(List.of("--time-format", RFC3339));

        var stdout = String.join(System.lineSeparator(), this.executor.call(CMD_LIST, args));
        log.info("{}{}", System.lineSeparator(), stdout);

        return objectMapper.readValue(stdout, RELEASES_TYPEREF)
                .stream().map(HelmRelease.class::cast).toList();
    }


}
@JsonIgnoreProperties(ignoreUnknown = true)
record DefaultHelmRelease(@JsonProperty("app_version") String appVersion,
                          String chart,
                          String name,
                          String namespace,
                          String revision,
                          String status,
                          ZonedDateTime updated) implements HelmRelease {

}
