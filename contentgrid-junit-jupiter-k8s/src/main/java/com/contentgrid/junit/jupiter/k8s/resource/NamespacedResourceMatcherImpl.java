package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Objects;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class NamespacedResourceMatcherImpl<T extends HasMetadata> implements ResourceMatcher.NamespacedResourceMatcher<T> {
    @NonNull
    private final ResourceMatcher<T> delegate;
    @Getter
    @NonNull
    private final String namespace;

    @Override
    public boolean test(T t) {
        return Objects.equals(namespace, t.getMetadata().getNamespace()) && delegate.test(t);
    }

    @Override
    public ResourceMatcher<T> inNamespace(@NonNull String namespace) {
        return new NamespacedResourceMatcherImpl<>(delegate, namespace);
    }
}
