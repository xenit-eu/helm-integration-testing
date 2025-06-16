package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;

public interface AwaitableResourceInstantiator<T extends KubernetesResource> {
    AwaitableResource instantiate(KubernetesClient kubernetesClient, AwaitableResourceFactory factory, T resource);
}
