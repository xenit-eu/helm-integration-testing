package com.contentgrid.testcontainers.k3s.customizer;

import java.util.Arrays;
import java.util.function.UnaryOperator;

/**
 * A collection of {@link K3sContainerCustomizer}s
 */
public interface K3sContainerCustomizers {

    /**
     * Registers customizers to this collection
     * Every class of customizer can only be registered once. Registering the same class of customizer multiple times is an error
     *
     * @param customizers The customizers to add to the collection
     */
    default K3sContainerCustomizers customize(K3sContainerCustomizer ...customizers) {
        return customize(Arrays.asList(customizers));
    }

    /**
     * Registers customizers to this collection
     * Every class of customizer can only be registered once. Registering the same class of customizer multiple times is an error
     *
     * @param customizers The customizers to add to the collection
     */
    K3sContainerCustomizers customize(Iterable<? extends K3sContainerCustomizer> customizers);

    /**
     * Configures a customizer in this collection
     * If the customizer of that type does not exist yet, it is created
     *
     * @param customizerClass The class of the customizer to configure (and create)
     */
    default <T extends K3sContainerCustomizer> K3sContainerCustomizers configure(Class<T> customizerClass) {
        return configure(customizerClass, UnaryOperator.identity());
    }

    /**
     * Configures a customizer in the container.
     * If the customizer of that type does not exist yet, it is created
     *
     * @param customizerClass The class of the customizer to configure (and create)
     * @param configurer Configuration function to apply on the customizer
     */
    <T extends K3sContainerCustomizer> K3sContainerCustomizers configure(Class<T> customizerClass,
            UnaryOperator<T> configurer);
}
