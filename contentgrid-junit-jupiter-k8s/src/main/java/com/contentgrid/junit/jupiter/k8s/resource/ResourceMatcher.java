package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.NonNull;

/**
 * Matchers for resources
 * @param <T> The type of the resource
 */
public interface ResourceMatcher<T extends HasMetadata> extends Predicate<T> {

    /**
     * Match a resource based on its labels
     * @param labels All these labels must be present on the resource
     * @param <T> The type of the resource
     */
    static <T extends HasMetadata> ResourceMatcher<T> labelled(@NonNull Map<String, String> labels) {
        return o -> labels.entrySet().stream()
                .allMatch(entry -> Objects.equals(o.getMetadata().getLabels().get(entry.getKey()), entry.getValue()));
    }

    /**
     * Match a resource based on its annotations
     * @param annotations All these annotations must be present on the resource
     * @param <T> The type of the resource
     */
    static <T extends HasMetadata> ResourceMatcher<T> annotated(@NonNull Map<String, String> annotations) {
        return o -> annotations.entrySet().stream()
                .allMatch(entry -> Objects.equals(o.getMetadata().getAnnotations().get(entry.getKey()), entry.getValue()));
    }

    /**
     * Match a resource based on its name
     * @param names The name of the resource must be one of these names
     * @param <T> The type of the resource
     */
    static <T extends HasMetadata> ResourceMatcher<T> named(@NonNull String... names) {
        var nameSet = Set.of(names);
        return o -> nameSet.contains(o.getMetadata().getName());
    }

    /**
     * Apply the matcher inside a specific namespace
     * @param namespace The namespace where the matcher must be applied to
     */
    default ResourceMatcher<T> inNamespace(@NonNull String namespace) {
        return new NamespacedResourceMatcherImpl<>(this, namespace);
    }

    interface NamespacedResourceMatcher<T extends HasMetadata> extends ResourceMatcher<T> {
        String getNamespace();
    }
}
