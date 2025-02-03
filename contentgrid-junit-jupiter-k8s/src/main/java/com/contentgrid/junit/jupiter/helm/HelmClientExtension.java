package com.contentgrid.junit.jupiter.helm;

import com.contentgrid.helm.Helm;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

public class HelmClientExtension implements HasHelmClient, BeforeAllCallback, BeforeEachCallback, ParameterResolver {

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

    private static List<Field> findFields(ExtensionContext context, Class<?> fieldType, Predicate<Field> predicate) {
        return ReflectionSupport.findFields(
                context.getRequiredTestClass(),
                field -> predicate.test(field) && fieldType.equals(field.getType()),
                HierarchyTraversalMode.TOP_DOWN);
    }

    private void setFieldValue(Field field, Object entity, Object value) throws IllegalAccessException {
        final boolean isAccessible = field.canAccess(entity);
        field.setAccessible(true);
        field.set(entity, value);
        field.setAccessible(isAccessible);
    }
}
