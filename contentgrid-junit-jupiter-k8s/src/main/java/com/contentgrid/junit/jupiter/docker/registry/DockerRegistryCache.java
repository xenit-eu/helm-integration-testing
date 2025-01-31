package com.contentgrid.junit.jupiter.docker.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs a docker registry mirror as a pull through cache in a docker container using TestContainers.
 *
 * <p>Note that this is NOT useful in normal usage patterns with TestContainers. However, if you run a Kubernetes
 * cluster in a Docker container using @TestContainers or @KubernetesTestCluster, this Kubernetes does not have access
 * to your local docker daemon. This means it will pull the images over the internet for every run.
 *
 * <p>DockerRegistryMirror will try to authenticate to proxied registries with the same strategy as TestContainers:
 * - Environment variables: `DOCKER_AUTH_CONFIG`
 * - Docker config at location specified in `DOCKER_CONFIG` or at `{HOME}/.docker/config.json`
 *
 * <p>This behaviour can be disabled with the system property {@code contentgrid.registryCache.disabled},
 * as it might not be desired in continuous integration tests.
 *
 * <pre>
 *
 *    Example:
 *
 *    &#064;KubernetesTestCluster
 *    &#064;DockerRegistryMirror(name="docker.io", url="https://registry-1.docker.io");
 *    class KubernetesIntegrationTest { ... }
 *
 * </pre>
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerRegistryCacheExtension.class)
@Repeatable(DockerRegistriesCache.class)
public @interface DockerRegistryCache {

    /**
     * (Required) The name of the registry.
     */
    String name();

    /**
     * (Optional) Configure this registry as a pull-through cache of a remote repository endpoint.
     */
    String proxy() default "";



    /**
     * (Optional) If a value provided, the configured host path will be used for storage.
     * By default, a named docker volume will be used.
     */
    String hostPath() default "";

}
