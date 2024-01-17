package com.contentgrid.helm;

import java.net.URI;
import java.util.List;
import lombok.Value;

public interface HelmDependencyCommand {

    List<HelmDependency> list();

    void build();

    @Value
    class DefaultHelmDependency implements HelmDependency {
        String name;
        String version;
        URI repository;
        String status;
    }

    interface HelmDependency {

        String getName();

        String getVersion();

        URI getRepository();

        String getStatus();
    }
}

