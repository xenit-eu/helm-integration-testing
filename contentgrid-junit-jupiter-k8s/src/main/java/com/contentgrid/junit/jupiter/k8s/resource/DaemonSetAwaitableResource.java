package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import java.util.Objects;
import lombok.NonNull;

class DaemonSetAwaitableResource extends AbstractAwaitableResourceWithChildren<DaemonSet, Pod> {

    public DaemonSetAwaitableResource(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, DaemonSet item) {
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
        return Objects.equals(item.getStatus().getNumberReady(), item.getStatus().getDesiredNumberScheduled());
    }
}
