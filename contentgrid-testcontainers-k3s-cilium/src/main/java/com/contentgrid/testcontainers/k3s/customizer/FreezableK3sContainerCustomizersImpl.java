package com.contentgrid.testcontainers.k3s.customizer;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.SneakyThrows;
import org.testcontainers.k3s.K3sContainer;

/**
 * Implementation of {@link K3sContainerCustomizers} that can be frozen after the container has been started
 */
public class FreezableK3sContainerCustomizersImpl implements K3sContainerCustomizer, K3sContainerCustomizers {
    private boolean isFrozen;
    private final Map<Class<? extends K3sContainerCustomizer>, K3sContainerCustomizer> customizers = new LinkedHashMap<>();

    @Override
    public K3sContainerCustomizers customize(Iterable<? extends K3sContainerCustomizer> customizersToRegister) {
        checkFrozen();
        for (var customizer : customizersToRegister) {
            var existing = customizers.putIfAbsent(customizer.getClass(), customizer);
            if(existing != null) {
                throw new IllegalArgumentException("Customizer %s is already registered".formatted(customizer.getClass()));
            }
            customizer.onRegister(this);
        }
        return this;
    }

    @Override
    public <T extends K3sContainerCustomizer> K3sContainerCustomizers configure(Class<T> customizerClass, UnaryOperator<T> configurer) {
        checkFrozen();
        customizers.compute(customizerClass, (clazz, existing) -> {
            if(existing == null) {
                existing = instantiate(customizerClass);
                existing.onRegister(this);
            }

            return configurer.apply((T)existing);
        }).onConfigure(this);

        return this;
    }

    @SneakyThrows(Throwable.class)
    private static <T extends K3sContainerCustomizer> T instantiate(Class<T> clazz)  {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.trySetAccessible();
            return constructor.newInstance();
        } catch (InstantiationException|NoSuchMethodException|IllegalAccessException e) {
            throw new IllegalArgumentException("%s must have a public no-args constructor".formatted(clazz), e);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    /**
     * Freeze the customizers, so they can not be changed anymore
     */
    public void freeze() {
        isFrozen = true;
    }

    private void checkFrozen() {
        if(isFrozen) {
            throw new IllegalStateException("Customizers are frozen because the container is already started");
        }
    }

    @Override
    public void customize(K3sContainer container) {
        customizers.forEach((clazz, customizer) -> customizer.customize(container));
    }


}
