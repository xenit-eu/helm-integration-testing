package com.contentgrid.helm;

import com.contentgrid.helm.impl.DefaultHelmClientBuilder;
import java.util.Map;

public interface HelmClient {

    HelmListCommand list();

    HelmDependencyCommand dependency();

    HelmRepositoryCommand repository();

    HelmInstallCommand install();

    HelmTemplateCommand template();

    /**
     * Readonly view of the environment variables
     *
     * @return env vars
     */
    Map<String, String> environment();

    static HelmClientBuilder builder() {
        return new DefaultHelmClientBuilder();
    }

}

