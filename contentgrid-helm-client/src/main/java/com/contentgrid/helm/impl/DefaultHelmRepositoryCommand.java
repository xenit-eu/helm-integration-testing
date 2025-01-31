package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmRepositoryCommand;
import com.contentgrid.helm.HelmRepositoryCommand.HelmRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class DefaultHelmRepositoryCommand implements HelmRepositoryCommand {


    private static final String CMD_REPOSITORY = "repo";
    private static final TypeReference<List<DefaultHelmRepository>> TYPEREF_REPOSITORIES = new TypeReference<>() {
    };

    @NonNull
    private final CommandExecutor executor;

    @NonNull
    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public List<HelmRepository> list() {
        var stdout = this.executor.call(CMD_REPOSITORY, "list", "--output", "json");

        return this.objectMapper.readValue(stdout, TYPEREF_REPOSITORIES)
                .stream().map(HelmRepository.class::cast).toList();
    }

    @Override
    @SneakyThrows
    public void add(@NonNull String name, @NonNull URI repository) {
        this.executor.call(CMD_REPOSITORY, "add", name, repository.toString());
    }
}

record DefaultHelmRepository(String name, String url) implements HelmRepository {

}
