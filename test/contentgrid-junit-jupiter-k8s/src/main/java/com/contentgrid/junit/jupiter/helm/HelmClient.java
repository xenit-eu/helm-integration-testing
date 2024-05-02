package com.contentgrid.junit.jupiter.helm;

import com.contentgrid.helm.Helm;
import com.contentgrid.junit.jupiter.k8s.KubernetesTestCluster;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;


/**
 * {@code @HelmClient} is a JUnit Jupiter extension that automatically injects a {@link Helm} client into
 * JUnit Jupiter test instance fields, static fields, and method arguments.
 *
 * <p>The {@link Helm} client will be configured automatically using the kubeconfig found in the system property
 * {@code kubeconfig} or the environment variable {@code KUBECONFIG}.
 *
 * <p>If the fabric8 {@link io.fabric8.junit.jupiter.NamespaceExtension} is activated, the {@link Helm} client
 * will utilize the associated namespace as the default namespace.
 *
 * <p>{@code @HelmClient} integrates seamlessly with {@link KubernetesTestCluster @KubernetesTestCluster}, if
 * the {@code @KubernetesTestCluster} annotation is declared before {@code @HelmClient}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(HelmClientExtension.class)
@Inherited
public @interface HelmClient {

}
