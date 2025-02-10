package com.contentgrid.junit.jupiter.externalsecrets.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

public class SecretStoreModel {

    private final String apiVersion = "external-secrets.io/v1beta1";
    private final String kind;
    private String name;
    private List<SecretData> secrets;

    public SecretStoreModel(String name, boolean isCluster) {
        this.name = name;
        this.secrets = new ArrayList<>();
        if (isCluster) {
            this.kind = "ClusterSecretStore";
        } else {
            this.kind = "SecretStore";
        }
    }

    public void setSecrets(Map<String, String> secrets) {
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            this.secrets.add(new SecretData(entry.getKey(), entry.getValue()));
        }
    }

    public String toYaml() {
        Map<String, Object> yamlMap = new HashMap<>();
        yamlMap.put("apiVersion", apiVersion);
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

    public static class SecretData {
        private String key;
        private String value;

        public SecretData(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("key", key);
            map.put("value", value);
            return map;
        }
    }
}