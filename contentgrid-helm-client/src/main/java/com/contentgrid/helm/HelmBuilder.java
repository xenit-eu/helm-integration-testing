package com.contentgrid.helm;

import java.nio.file.Path;

public interface HelmBuilder {

    Helm build();

    HelmBuilder withRepositoryConfigTempFile();

    HelmBuilder withWorkingDirectory(Path workingDirectory);

    HelmBuilder withKubeConfig(Path kubeConfig);

    HelmBuilder withNamespace(String namespace);

    HelmBuilder withDataHome(Path dataHome);

    HelmBuilder withRepositoryConfig(Path repositoryConfig);
}
