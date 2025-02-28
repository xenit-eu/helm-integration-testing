package com.contentgrid.junit.jupiter.externalsecrets;

import com.contentgrid.junit.jupiter.externalsecrets.model.SecretStoreModel;
import com.contentgrid.junit.jupiter.externalsecrets.model.SecretStoreModel.SecretData;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private final List<SecretData> secrets = new ArrayList<>();
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
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            this.secrets.add(new SecretData(entry.getKey(), entry.getValue()));
        }
        putSecretsInKubernetes();
    }

    /**
     * Add secrets to the secret store
     * @param secrets list of secrets
     */
    public void addSecrets(List<SecretData> secrets) {
        this.secrets.addAll(secrets);
        putSecretsInKubernetes();
    }

    private void putSecretsInKubernetes() {
        var secretStoreModel = new SecretStoreModel(this.name, this.isCluster);
        secretStoreModel.setSecrets(this.secrets);
        client.resourceList(secretStoreModel.toYaml()).serverSideApply();
    }

}
