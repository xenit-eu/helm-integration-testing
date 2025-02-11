package com.contentgrid.junit.jupiter.externalsecrets;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ExternalSecretsExtensionTest {

    @Nested
    @KubernetesTestCluster
    @FakeSecretStore
    class AutowiringTest {

        static SecretStore staticSecretStore;
        static ClusterSecretStore staticClusterSecretStore;

        SecretStore instanceSecretStore;
        ClusterSecretStore instanceClusterSecretStore;

        // avoid injection with Object
        static Object secretStoreParameter;
        static Object clusterSecretStoreParameter;

        @BeforeAll
        static void beforeAll(SecretStore parameterSecretStore, ClusterSecretStore parameterClusterSecretStore) {
            secretStoreParameter = parameterSecretStore;
            clusterSecretStoreParameter = parameterClusterSecretStore;
        }

        static KubernetesClient client;

        @Test
        void staticInjection() {
            assertEquals("static-secret-store", staticSecretStore.getName());
            assertEquals("static-cluster-secret-store", staticClusterSecretStore.getName());

            var staticKey1 = "staticKey1";
            var staticValue1 = "staticValue1";
            staticSecretStore.addSecrets(Map.of(staticKey1, staticValue1
                    , "staticKey2", "staticValue2"));

            var secretName = "staticExternalSecret".toLowerCase();
            deployExternalSecret(secretName, "SecretStore", staticSecretStore.getName(), staticKey1, staticKey1);
            assertSecretValue(secretName, staticKey1, staticValue1);

            var staticClusterKey2 = "staticClusterKey2";
            var staticClusterValue2 = "staticClusterValue2";
            staticClusterSecretStore.addSecrets(Map.of("staticClusterKey1", "staticClusterValue1"
                    , staticClusterKey2, staticClusterValue2));
            deployExternalSecret(secretName, "ClusterSecretStore", staticClusterSecretStore.getName(), staticClusterKey2, staticClusterKey2);
            assertSecretValue(secretName, staticClusterKey2, staticClusterValue2);
        }

        static String externalSecret =
                    """
                    apiVersion: external-secrets.io/v1beta1
                    kind: ExternalSecret
                    metadata:
                      name: ${name}
                    spec:
                      secretStoreRef:
                        kind: ${secretStoreKind}
                        name: ${secretStoreName}
                      data:
                        - secretKey: ${secretKey}
                          remoteRef:
                            key: ${remoteKey}
                    """;
        static void deployExternalSecret(String name, String kind, String secretStoreName, String secretKey, String remoteKey) {
            client.resourceList(externalSecret
                    .replace("${name}", name)
                    .replace("${secretStoreKind}", kind)
                    .replace("${secretStoreName}", secretStoreName)
                    .replace("${secretKey}", secretKey)
                    .replace("${remoteKey}", remoteKey)).serverSideApply();
        }

        static void assertSecretValue(String secretName, String secretKey, String expectedValue) {
            var secret = await().atMost(10, SECONDS).until(() -> client.secrets().withName(secretName).get(), Objects::nonNull);
            var secretValue = new String(Base64.getDecoder().decode(secret.getData().get(secretKey)));
            assertEquals(expectedValue, secretValue);
        }

        @Test
        void instanceInjection() {
            assertEquals("instance-secret-store", instanceSecretStore.getName());
            assertEquals("instance-cluster-secret-store", instanceClusterSecretStore.getName());
        }

        @Test
        void parameterInjection() {
            assertEquals("before-all-parameter-secret-store", ((SecretStore) secretStoreParameter).getName());
            assertEquals("before-all-parameter-cluster-secret-store", ((ClusterSecretStore) clusterSecretStoreParameter).getName());
        }

    }

}
