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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(HelmClientExtension.class)
@ExtendWith(HelmTestExtension.class)
@Inherited
public @interface HelmTest {

    /**
     * Relative or absolute path to a local {@code Chart.yaml} or its containing directory on the host filesystem.
     * Alternatively, also classpath resources can be referenced with the prefix {@code classpath:}
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
