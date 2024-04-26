package com.contentgrid.helm;

import java.nio.file.Path;

public interface HelmBuilder {

    Helm build();

    HelmBuilder withRepositoryConfigTempFile();

    HelmBuilder withWorkingDirectory(java.nio.file.Path workingDirectory);

    HelmBuilder withKubeConfig(java.nio.file.Path kubeConfig);

    HelmBuilder withNamespace(String namespace);

    HelmBuilder withDataHome(Path dataHome);

    HelmBuilder withRepositoryConfig(java.nio.file.Path repositoryConfig);
}
