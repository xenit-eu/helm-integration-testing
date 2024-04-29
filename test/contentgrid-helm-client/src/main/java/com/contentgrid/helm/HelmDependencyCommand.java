package com.contentgrid.helm;

import java.net.URI;
import java.util.List;
import lombok.Value;

public interface HelmDependencyCommand {

    List<HelmDependency> list();

    void build();

    interface HelmDependency {

        String name();

        String version();

        URI repository();

        String status();
    }
}

