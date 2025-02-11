package com.contentgrid.junit.jupiter.externalsecrets;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * SecretStore from ExternalSecrets
 */
public class SecretStore extends AbstractSecretStore {

    /**
     * Constructor
     * @param name name of the secret store
     * @param client kubernetes client
     */
    public SecretStore(String name, KubernetesClient client) {
        super(name, client, false);
    }

}
