package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.NonNull;

public interface ConfigurableResourceSet extends ResourceSet, ResourceMatchingSpec<ConfigurableResourceSet> {
    static ConfigurableResourceSet of(@NonNull KubernetesClient client) {
        return new ConfigurableResourceSetImpl(client);
    }

}
