package com.contentgrid.junit.jupiter.k8s.resource;

import java.util.stream.Stream;

public interface ResourceSet extends AutoCloseable {

    /**
     * @return Stream of all matching resources
     */
    Stream<? extends AwaitableResource> stream();

    /**
     * Dispose of the resources allocated when streaming matching resources
     */
    @Override
    void close();
}
