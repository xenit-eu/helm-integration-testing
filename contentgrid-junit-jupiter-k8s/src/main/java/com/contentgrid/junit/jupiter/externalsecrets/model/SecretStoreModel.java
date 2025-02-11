package com.contentgrid.junit.jupiter.externalsecrets.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Model for secret store
 */
public class SecretStoreModel {

    private static final String API_VERSION = "external-secrets.io/v1beta1";
    private final String kind;
    private final String name;
    private final List<SecretData> secrets;

    /**
     * Constructor
     * @param name name of the secret store
     * @param isCluster if true, ClusterSecretStore otherwise SecretStore
     */
    public SecretStoreModel(String name, boolean isCluster) {
        this.name = name;
        this.secrets = new ArrayList<>();
        if (isCluster) {
            this.kind = "ClusterSecretStore";
        } else {
            this.kind = "SecretStore";
        }
    }

    /**
     * Set secrets
     * @param secrets map of secrets
     */
    public void setSecrets(Map<String, String> secrets) {
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            this.secrets.add(new SecretData(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Convert to yaml
     * @return yaml string
     */
    public String toYaml() {
        Map<String, Object> yamlMap = new HashMap<>();
        yamlMap.put("apiVersion", API_VERSION);
        yamlMap.put("kind", kind);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        yamlMap.put("metadata", metadata);

        Map<String, Object> provider = new HashMap<>();
        Map<String, Object> fake = new HashMap<>();
        List<Map<String, String>> dataList = new ArrayList<>();

        for (SecretData secret : secrets) {
            dataList.add(secret.toMap());
        }

        fake.put("data", dataList);
        provider.put("fake", fake);
        yamlMap.put("spec", Map.of("provider", provider));

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        return yaml.dumpAs(yamlMap, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
    }

    /**
     * Secret data
     */
    public static class SecretData {
        private final String key;
        private final String value;

        /**
         * Constructor
         * @param key key
         * @param value value
         */
        public SecretData(String key, String value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Convert to map
         * @return map
         */
        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("key", key);
            map.put("value", value);
            return map;
        }
    }
}