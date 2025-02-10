package com.contentgrid.junit.jupiter.externalsecrets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
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

        @Test
        void staticInjection() {
            assertEquals("static-secret-store", staticSecretStore.getName());
            assertEquals("static-cluster-secret-store", staticClusterSecretStore.getName());
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
