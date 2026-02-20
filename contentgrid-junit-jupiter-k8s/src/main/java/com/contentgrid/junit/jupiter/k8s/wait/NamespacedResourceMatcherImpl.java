package com.contentgrid.junit.jupiter.k8s.wait;

import com.contentgrid.junit.jupiter.k8s.resource.ResourceMatcher.NamespacedResourceMatcher;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Objects;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Deprecated(forRemoval = true, since = "0.1.4")
@RequiredArgsConstructor
@SuppressWarnings("removal")
class NamespacedResourceMatcherImpl<T extends HasMetadata> implements NamespacedResourceMatcher<T>, ResourceMatcher<T> {
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
