package com.contentgrid.helm.impl;

import com.contentgrid.helm.Helm;
import com.contentgrid.helm.HelmBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class DefaultHelmBuilder implements HelmBuilder {

    @With
    private Path workingDirectory;

    @With
    private Path kubeConfig;

    @With
    private String namespace;

    /**
     * The path to set an alternative location for storing Helm data.
     */
    @With
    private Path dataHome;

    /**
     * The path to the repositories configuration file.
     */
    @With
    private Path repositoryConfig;

    @Override
    @SneakyThrows
    public DefaultHelmBuilder withRepositoryConfigTempFile() {
        return this.withRepositoryConfig(Files.createTempFile("helm-", "-repositories.yaml"));
    }

    @Override
    public Helm build() {

        var env = createEnv();
        var exec = new ProcessBuilderHelmExecutor(env, workingDirectory != null ? workingDirectory.toFile() : null);

        var objectMapper = new ObjectMapper().findAndRegisterModules();

        return new DefaultHelm(exec, objectMapper);
    }

    private Map<String, String> createEnv() {
        var env = new HashMap<String, String>();
        if (this.kubeConfig != null) {
            env.put("KUBECONFIG", this.kubeConfig.toAbsolutePath().toString());
            if (!Files.exists(this.kubeConfig)) {
                log.warn("KUBECONFIG={} - but file not found", this.kubeConfig.toAbsolutePath());
            }
        }

        if (this.namespace != null) {
            env.put("HELM_NAMESPACE", this.namespace);
        }

        if (this.dataHome != null) {
            env.put("HELM_DATA_HOME", this.dataHome.toAbsolutePath().toString());
        }

        if (this.repositoryConfig != null) {
            env.put("HELM_REPOSITORY_CONFIG", this.repositoryConfig.toAbsolutePath().toString());
        }

        return env;
    }

}
