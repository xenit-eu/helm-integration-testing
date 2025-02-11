package com.contentgrid.junit.jupiter.helm;

import static com.contentgrid.junit.jupiter.helpers.FieldHelper.findFields;

import com.contentgrid.helm.Helm;
import io.fabric8.junit.jupiter.BaseExtension;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class HelmClientExtension implements HasHelmClient, BeforeAllCallback, BeforeEachCallback, ParameterResolver, BaseExtension {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // autowire static fields
        for (Field field : findFields(context, Helm.class, f -> Modifier.isStatic(f.getModifiers()))) {
            setFieldValue(field, null, getHelmClient(context));
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // autowire instance fields
        for (Field field : findFields(context, Helm.class, f -> !Modifier.isStatic(f.getModifiers()))) {
            setFieldValue(field, context.getRequiredTestInstance(), getHelmClient(context));
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(Helm.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return getHelmClient(extensionContext);
    }

}
