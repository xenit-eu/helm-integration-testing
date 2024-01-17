package com.contentgrid.helm;

import java.nio.file.Path;

public interface HelmClientBuilder {

    HelmClient build();

    HelmClientBuilder withRepositoryConfigTempFile();

    HelmClientBuilder withWorkingDirectory(java.nio.file.Path workingDirectory);

    HelmClientBuilder withKubeConfig(java.nio.file.Path kubeConfig);

    HelmClientBuilder withNamespace(String namespace);

    HelmClientBuilder withDataHome(Path dataHome);

    HelmClientBuilder withRepositoryConfig(java.nio.file.Path repositoryConfig);
}
