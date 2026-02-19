package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.NonNull;

public interface ResourceMatchingSpec<R extends ResourceMatchingSpec<R>> {

    /**
     * Include certain resources to the wait
     * @param clazz Resource type to include
     * @param matcher Matcher for the resource type
     */
    <T extends HasMetadata> R include(
            @NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher
    );

    /**
     * Exclude certain resources from the wait
     * @param clazz Resource type to exclude
     * @param matcher Matcher for the resource type
     */
    <T extends HasMetadata> R exclude(
            @NonNull Class<T> clazz,
            @NonNull ResourceMatcher<? super T> matcher
    );
}
