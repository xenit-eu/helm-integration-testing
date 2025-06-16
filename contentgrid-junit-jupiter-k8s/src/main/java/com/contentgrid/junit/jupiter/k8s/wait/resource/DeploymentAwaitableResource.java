package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Listable;
import io.fabric8.kubernetes.client.readiness.Readiness;
import lombok.NonNull;

public class DeploymentAwaitableResource extends AbstractAwaitableResourceWithChildren<Deployment, ReplicaSet> {

    public DeploymentAwaitableResource(@NonNull KubernetesClient client, @NonNull AwaitableResourceFactory factory, @NonNull Deployment item) {
        super(client, factory, item);
    }

    @Override
    public boolean isReady() {
        return Readiness.isDeploymentReady(item);
    }


    @Override
    protected Listable<? extends KubernetesResourceList<ReplicaSet>> createChildResourcesFilter() {
        var selector = item.getSpec().getSelector().getMatchLabels();
        return client.apps().replicaSets()
                .inNamespace(item.getMetadata().getNamespace())
                .withLabels(selector);
    }

}
