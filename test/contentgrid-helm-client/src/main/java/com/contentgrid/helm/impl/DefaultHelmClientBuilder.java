package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmClient;
import com.contentgrid.helm.HelmClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
public class DefaultHelmClientBuilder implements HelmClientBuilder {

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
    public DefaultHelmClientBuilder withRepositoryConfigTempFile() {
        return this.withRepositoryConfig(Files.createTempFile("helm-", "-repositories.yaml"));
    }

    @Override
    public HelmClient build() {

        var env = createEnv();
        var exec = new ProcessBuilderHelmExecutor(env, workingDirectory != null ? workingDirectory.toFile() : null);

        var objectMapper = new ObjectMapper().findAndRegisterModules();

        return new DefaultHelmClient(exec, objectMapper);
    }

    private Map<String, String> createEnv() {
        var env = new HashMap<String, String>();
        if (this.kubeConfig != null && Files.exists(this.kubeConfig)) {
            env.put("KUBECONFIG", this.kubeConfig.toAbsolutePath().toString());
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
