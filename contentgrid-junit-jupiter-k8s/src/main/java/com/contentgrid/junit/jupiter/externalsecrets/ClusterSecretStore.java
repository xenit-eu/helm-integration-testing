package com.contentgrid.junit.jupiter.externalsecrets;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Cluster secret store
 */
public class ClusterSecretStore extends AbstractSecretStore {

    /**
     * Constructor
     * @param name name of the secret store
     * @param client kubernetes client
     */
    public ClusterSecretStore(String name, KubernetesClient client) {
        super(name, client, true);
    }

}
