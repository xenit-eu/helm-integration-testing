package com.contentgrid.junit.jupiter.externalsecrets;

import io.fabric8.kubernetes.client.KubernetesClient;

public class SecretStore extends AbstractSecretStore {

    public SecretStore(String name, KubernetesClient client) {
        super(name, client, false);
    }

}
