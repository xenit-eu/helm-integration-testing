package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import io.fabric8.kubernetes.client.readiness.Readiness;
import lombok.NonNull;

public class StatefulSetAwaitableResource extends AbstractAwaitableResourceWithChildren<StatefulSet, Pod> {

    public StatefulSetAwaitableResource(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, StatefulSet item) {
        super(client, factory, item);
    }

    @Override
    protected Listable<? extends KubernetesResourceList<? extends Pod>> createChildResourcesFilter() {
        return client.pods()
                .inNamespace(item.getMetadata().getNamespace())
                .withLabels(item.getMetadata().getLabels());
    }

    @Override
    public boolean isReady() {
        return Readiness.isStatefulSetReady(item);
    }
}
