package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;

public class AwaitableResourceFactory {
    private final Map<Class<? extends KubernetesResource>, AwaitableResourceInstantiator<KubernetesResource>> factories = new HashMap<>();

    public <T extends KubernetesResource> void registerFactory(Class<T> type, AwaitableResourceInstantiator<T> factory) {
        factories.put(type, (AwaitableResourceInstantiator<KubernetesResource>) factory);
    }

    public AwaitableResource instantiate(KubernetesClient client, KubernetesResource resource) {
        return factories.get(resource.getClass()).instantiate(client, this, resource);
    }

}
