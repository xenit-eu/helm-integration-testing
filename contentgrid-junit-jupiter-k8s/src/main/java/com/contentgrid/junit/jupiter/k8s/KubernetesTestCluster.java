package com.contentgrid.junit.jupiter.k8s;

import com.contentgrid.junit.jupiter.k8s.providers.K3sTestcontainersClusterProvider;
import com.contentgrid.junit.jupiter.k8s.providers.KubernetesClusterProvider;
import io.fabric8.junit.jupiter.KubernetesExtension;
import io.fabric8.junit.jupiter.NamespaceExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @KubernetesTestCluster} is a JUnit Jupiter extension to facilitate integration testing with a Kubernetes test
 * cluster. This annotation implies {@code @KubernetesTest} from fabric8's kubernetes-client, which will inject a
 * {@link io.fabric8.kubernetes.client.KubernetesClient} in any field of that type, with an ephemeral kubernetes
 * namespace, which will be deleted again after the test suite execution.
 *
 * <p>
 * A configurable ordered list of {@link KubernetesClusterProvider}s is examined, to find one that can provision or
 * provide a Kubernetes cluster.
 *
 * <p>
 * This has the (intentional) side-effect that fabric8 autoconfiguration no longer automatically picks up your
 * systems' default kubeconfig in {@code ~/.kube/config} or {@code KUBECONFIG} environment variable. This means
 * integration tests will no longer run against a production kubernetes cluster by accident :grimacing:
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KubernetesTestClusterExtension.class)
@ExtendWith(NamespaceExtension.class)
@ExtendWith(KubernetesExtension.class)
@Inherited
public @interface KubernetesTestCluster {

    Class<? extends KubernetesClusterProvider>[] providers() default {K3sTestcontainersClusterProvider.class};
}
