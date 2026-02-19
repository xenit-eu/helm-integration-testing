package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import io.fabric8.kubernetes.client.readiness.Readiness;
import lombok.NonNull;

class ReplicaSetAwaitableResource extends AbstractAwaitableResourceWithChildren<ReplicaSet, Pod> {

    public ReplicaSetAwaitableResource(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, @NonNull ReplicaSet item) {
        super(client, factory, item);
    }

    @Override
    protected Listable<? extends KubernetesResourceList<? extends Pod>> createChildResourcesFilter() {
        return client.pods()
                .inNamespace(item.getMetadata().getNamespace())
                .withLabels(item.getSpec().getSelector().getMatchLabels());
    }

    @Override
    public boolean isReady() {
        return Readiness.isReplicaSetReady(item);
    }

}
