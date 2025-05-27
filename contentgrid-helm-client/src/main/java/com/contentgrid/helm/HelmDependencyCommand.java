package com.contentgrid.helm;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public interface HelmDependencyCommand {

    List<HelmDependency> list(String chart);

    default List<HelmDependency> list(Path chartPath) {
        return list(chartPath.toAbsolutePath().normalize().toString());
    }

    default List<HelmDependency> list() {
        return list(".");
    }

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

