package com.contentgrid.junit.jupiter.helm;

import com.contentgrid.helm.Helm;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @HelmTest} is a JUnit Jupiter extension that facilitates testing helm charts.
 *
 * <p>This annotation implies {@link HelmClient @HelmClient}, which injects a configured {@link Helm} instance into
 * JUnit Jupiter test instance fields, static fields, and method arguments.
 *
 * The referenced chart gets copied to the temporary working directory of the {@link Helm} client and chart repositories
 * are installed in the ephemeral repository list of the {@link Helm} client.
 *
 * @deprecated Use the {@link HelmChart @HelmChart} annotation on a {@link HelmChartHandle} field instead
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({HelmClientExtension.class, HelmChartHandleExtension.class, HelmTestExtension.class})
@Inherited
@Deprecated(since = "0.0.8", forRemoval = true)
public @interface HelmTest {

    /**
     * Path to a local {@code Chart.yaml} or its containing directory. This path can be either a path to a file or
     * directory on the host filesystem using a {@code file:} prefix, or a classpath resource using the
     * {@code classpath:} prefix. Omitting a prefix will be interpreted as a host filesystem path.
     *
     * @return the host path or classpath Chart location
     */
    String chart();

    /**
     * Whether repositories from {@code Chart.yaml} should be automatically added to the {@link Helm} client.
     * Defaults to {@code true}.
     *
     * @return if the repositories from {@code Chart.yaml} should be added
     */
    boolean addChartRepositories() default true;
}
