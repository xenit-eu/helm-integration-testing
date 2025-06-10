package com.contentgrid.junit.jupiter.helm;

import com.contentgrid.helm.Helm;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code HelmChart} is an annotation to inject a specific chart for the {@link HelmClient} JUnit Jupiter extension
 * <p>
 * This annotation requires the {@link HelmClient @HelmClient} annotation, which creates the configured {@link Helm} instance.
 * <p>
 * The referenced chart gets copied to the temporary working directory of the {@link Helm} client and chart repositories
 * are installed in the ephemeral repository list of the {@link Helm} client.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HelmChart {

    /**
     * A chart reference, a path to a packaged chart, a path to an unpacked chart directory or a URL
     * <p>
     * Following chart formats are supported
     * <ul>
     *     <li>{@code file:} a directory on the host filesystem; relative to the current working directory
     *     <li>{@code classpath:} a resource on the classpath; relative to the test class
     *     <li>{@code oci://} an OCI helm chart. A version can optionally be supplied by using the format {@code oci://<repo>:<version>}
     * </ul>
     *
     * @return chart reference
     */
    String chart();

    /**
     * @return The namespace to install the helm chart into.
     * <p>
     * Defaults to using the namespace configured on the kubernetes client
     * <p>
     * When the {@link #NAMESPACE_ISOLATE} setting is used, an isolated namespace is created for the helm chart
     */
    String namespace() default NAMESPACE_DEFAULT;

    String NAMESPACE_DEFAULT = "\0\0";
    String NAMESPACE_ISOLATE = "\0\1";

    /**
     * Whether repositories from {@code Chart.yaml} should be automatically added to the {@link Helm} client.
     * Defaults to {@code true}.
     *
     * @return if the repositories from {@code Chart.yaml} should be added
     */
    boolean addChartRepositories() default true;

}
