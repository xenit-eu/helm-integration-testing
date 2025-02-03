package com.contentgrid.junit.jupiter.docker.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Runs one or more docker registries as a pull-through cache
 *
 * <pre>
 *
 *    Example:
 *
 *    &#064;KubernetesTestCluster
 *    &#064;DockerRegistriesCache({
 *            &#064;DockerRegistryCache(name="docker.io", url="https://registry-1.docker.io"),
 *            &#064;DockerRegistryCache(name="quay.io", url="https://quay.io/v2/")
 *    })
 *    class KubernetesIntegrationTest { ... }
 *
 * </pre>
 *
 * @see DockerRegistryCache
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DockerRegistriesCache {

    /** (Required) One or more field or property mapping overrides. */
    DockerRegistryCache[] value();

}

