package com.contentgrid.helm.impl;

import com.contentgrid.helm.Helm;
import com.contentgrid.helm.HelmDependencyCommand;
import com.contentgrid.helm.HelmInstallCommand;
import com.contentgrid.helm.HelmListCommand;
import com.contentgrid.helm.HelmRepositoryCommand;
import com.contentgrid.helm.HelmTemplateCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class DefaultHelm implements Helm {


    @NonNull
    private final CommandExecutor executor;

    @NonNull
    private final ObjectMapper objectMapper;


    @Override
    public HelmListCommand list() {
        return new DefaultHelmListCommand(this.executor, this.objectMapper);
    }

    @Override
    public HelmDependencyCommand dependency() {
        return new DefaultHelmDependencyCommand(this.executor);
    }

    @Override
    public HelmRepositoryCommand repository() {
        return new DefaultHelmRepositoryCommand(this.executor, this.objectMapper);
    }

    @Override
    public HelmInstallCommand install() {
        return new DefaultHelmInstallCommand(this.executor, this.objectMapper);
    }

    @Override
    public HelmTemplateCommand template() {
        return new DefaultHelmTemplateCommand(this.executor, this.objectMapper);
    }

    @Override
    public Map<String, String> environment() {
        return this.executor.environment();
    }

}
