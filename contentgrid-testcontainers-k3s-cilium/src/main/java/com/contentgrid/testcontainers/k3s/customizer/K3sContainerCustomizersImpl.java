package com.contentgrid.testcontainers.k3s.customizer;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.SneakyThrows;
import org.testcontainers.k3s.K3sContainer;

/**
 * Implementation of {@link K3sContainerCustomizers} that can be frozen after the container has been started
 */
public class K3sContainerCustomizersImpl implements K3sContainerCustomizer, K3sContainerCustomizers {
    private boolean isFrozen;
    private final Map<Class<? extends K3sContainerCustomizer>, K3sContainerCustomizer> customizers = new LinkedHashMap<>();
    private final Map<Class<? extends K3sContainerCustomizer>, UnaryOperator<K3sContainerCustomizer>> deferredConfigurers = new HashMap<>();

    @Override
    public K3sContainerCustomizers customize(Iterable<? extends K3sContainerCustomizer> customizersToRegister) {
        checkFrozen();
        for (var customizer : customizersToRegister) {
            var clazz = customizer.getClass();
            var existing = customizers.putIfAbsent(clazz, customizer);
            if (existing != null) {
                throw new IllegalArgumentException("Customizer %s is already registered".formatted(customizer.getClass()));
            }
            customizer = customizers.compute(
                    clazz,
                    (key, c) -> deferredConfigurers.getOrDefault(key, UnaryOperator.identity()).apply(c)
            );
            deferredConfigurers.remove(clazz);
            customizer.onRegister(this);

        }
        return this;
    }

    @Override
    public <T extends K3sContainerCustomizer> K3sContainerCustomizers configure(Class<T> customizerClass, UnaryOperator<T> configurer) {
        checkFrozen();
        if (!customizers.containsKey(customizerClass)) {
            customize(instantiate(customizerClass));
        }

        var customizer = customizers.compute(customizerClass, (key, c) -> configurer.apply((T)c));

        customizer.onConfigure(this);

        return this;
    }

    @Override
    public <T extends K3sContainerCustomizer> K3sContainerCustomizers maybeConfigure(Class<T> customizerClass,
            UnaryOperator<T> configurer) {
        checkFrozen();
        if (customizers.containsKey(customizerClass)) {
            return configure(customizerClass, configurer);
        }

        deferredConfigurers.compute(customizerClass, (_key, existingConfigurer) -> {
            if (existingConfigurer == null) {
                return (UnaryOperator<K3sContainerCustomizer>) configurer;
            } else {
                return customizer -> configurer.apply((T)existingConfigurer.apply(customizer));
            }

        });
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

    private void checkFrozen() {
        if(isFrozen) {
            throw new IllegalStateException("Customizers are frozen because the container is already started");
        }
    }

    @Override
    public void customize(K3sContainer container) {
        isFrozen = true;
        customizers.forEach((clazz, customizer) -> customizer.customize(container));
    }

}
