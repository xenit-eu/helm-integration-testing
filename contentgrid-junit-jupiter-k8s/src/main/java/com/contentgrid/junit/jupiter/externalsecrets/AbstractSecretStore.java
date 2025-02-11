package com.contentgrid.junit.jupiter.externalsecrets;

import com.contentgrid.junit.jupiter.externalsecrets.model.SecretStoreModel;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;

/**
 * Abstract class for secret store
 */
public abstract class AbstractSecretStore {

    /**
     * Name of the secret store
     * @return name of the secret store
     */
    @Getter
    @NonNull
    private final String name;

    private final Map<String, String> secrets = new HashMap<>();
    private final KubernetesClient client;
    private final boolean isCluster;

    /**
     * Constructor
     * @param name name of the secret store
     * @param client kubernetes client
     * @param isCluster if true, ClusterSecretStore otherwise SecretStore
     */
    protected AbstractSecretStore(String name, KubernetesClient client, boolean isCluster) {
        this.name = name;
        this.client = client;
        this.isCluster = isCluster;
    }

    /**
     * Add secrets to the secret store
     * @param secrets map of secrets
     */
    public void addSecrets(Map<String, String> secrets) {
        this.secrets.putAll(secrets);
        var secretStoreModel = new SecretStoreModel(this.name, this.isCluster);
        secretStoreModel.setSecrets(this.secrets);
        client.resourceList(secretStoreModel.toYaml()).serverSideApply();
    }

}
