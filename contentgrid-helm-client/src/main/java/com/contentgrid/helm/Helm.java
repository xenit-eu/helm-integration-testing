package com.contentgrid.helm;

import com.contentgrid.helm.impl.DefaultHelmBuilder;
import java.util.Map;

public interface Helm {

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

    static HelmBuilder builder() {
        return new DefaultHelmBuilder();
    }

}

