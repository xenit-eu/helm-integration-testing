package com.contentgrid.junit.jupiter.externalsecrets;

import io.fabric8.kubernetes.client.KubernetesClient;

public class ClusterSecretStore extends AbstractSecretStore {

    public ClusterSecretStore(String name, KubernetesClient client) {
        super(name, client, true);
    }

}
