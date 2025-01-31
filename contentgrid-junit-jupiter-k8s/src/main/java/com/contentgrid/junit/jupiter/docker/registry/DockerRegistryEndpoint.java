package com.contentgrid.junit.jupiter.docker.registry;

import java.net.URI;

public interface DockerRegistryEndpoint {

    URI getURI();

    String getName();
}
