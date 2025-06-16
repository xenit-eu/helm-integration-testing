package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import java.util.Objects;
import lombok.NonNull;

public class JobAwaitableResource extends AbstractAwaitableResourceWithChildren<Job, Pod> {

    public JobAwaitableResource(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, Job item) {
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
        return item.getStatus().getConditions()
                .stream()
                .anyMatch(completion -> Objects.equals(completion.getType(), "Complete") && Objects.equals(completion.getStatus(), "True"));
    }
}
