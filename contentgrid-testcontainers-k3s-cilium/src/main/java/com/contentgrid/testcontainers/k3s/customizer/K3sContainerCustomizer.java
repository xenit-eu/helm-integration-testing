package com.contentgrid.testcontainers.k3s.customizer;

import org.testcontainers.k3s.K3sContainer;

/**
 * An object that applies customizations to a {@link K3sContainer}
 */
public interface K3sContainerCustomizer {

    /**
     * Callback function that is called when the customizer is added to the container
     * @param customizers The customizers container
     */
    default void onRegister(K3sContainerCustomizers customizers) {

    }

    /**
     * Callback function that is called when the customizer is reconfigured
     * @param customizers The customizers container
     */
    default void onConfigure(K3sContainerCustomizers customizers) {

    }

    /**
     * Customizes the container before it is started
     * @param container The container to customize
     */
    void customize(K3sContainer container);
}
