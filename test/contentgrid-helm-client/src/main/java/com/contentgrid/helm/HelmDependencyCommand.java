package com.contentgrid.helm;

import com.contentgrid.helm.HelmInstallCommand.InstallOption;
import com.contentgrid.helm.HelmInstallCommand.InstallResult;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import lombok.Value;

public interface HelmDependencyCommand {

    List<HelmDependency> list();

    void build(String chart);

    default void build() {
        build(".");
    }

    default void build(Path chartPath) {
        this.build(chartPath.toAbsolutePath().normalize().toString());
    }

    interface HelmDependency {

        String name();

        String version();

        URI repository();

        String status();
    }
}

