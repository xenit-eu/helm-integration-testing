package com.contentgrid.junit.jupiter.externalsecrets;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @FakeSecretStore} is a JUnit Jupiter extension to facilitate integration testing with a fake External Secret
 * Store
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ExternalSecretsExtension.class)
@Inherited
public @interface FakeSecretStore {

}
