package com.contentgrid.junit.jupiter.externalsecrets;

import com.contentgrid.junit.jupiter.externalsecrets.model.SecretStoreModel;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

public abstract class AbstractSecretStore {

    @Getter
    private final String name;

    private final Map<String, String> secrets = new HashMap<>();
    private final KubernetesClient client;
    private final boolean isCluster;


    protected AbstractSecretStore(String name, KubernetesClient client, boolean isCluster) {
        this.name = name;
        this.client = client;
        this.isCluster = isCluster;
    }

    public void addSecrets(Map<String, String> secrets) {
        this.secrets.putAll(secrets);
        var secretStoreModel = new SecretStoreModel(this.name, this.isCluster);
        secretStoreModel.setSecrets(this.secrets);
        client.resourceList(secretStoreModel.toYaml()).serverSideApply();
    }

}
