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
 * {@code @HelmClient} is a JUnit Jupiter that autowires a {@link Helm} client into junit-jupiter test instance
 * fields, static fields and method arguments.
 *
 * <p>The {@link Helm} client will be automatically configured with kubeconfig found in System Property
 * {@code kubeconfig} or enviroment variable {@code KUBECONFIG}.
 *
 * <p>Additionally, if fabric8 {@link io.fabric8.junit.jupiter.NamespaceExtension} is activated, the {@link Helm} client
 * will use this namespace as the default namespace.
 *
 * <p>This behaviour means that it works well together with {@link KubernetesTestCluster @KubernetesTestCluster}, if
 * you add that annotation before {@code @HelmClient}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(HelmClientExtension.class)
@Inherited
public @interface HelmClient {

}
