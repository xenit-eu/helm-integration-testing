package com.contentgrid.junit.jupiter.helm;

import static com.contentgrid.junit.jupiter.helpers.FieldHelper.findFields;
import static com.contentgrid.junit.jupiter.helpers.FieldHelper.getFieldValue;
import static com.contentgrid.junit.jupiter.helpers.FieldHelper.setFieldValue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HelmChartHandleExtension implements HasHelmClient, BeforeEachCallback, BeforeAllCallback, AfterEachCallback,
        AfterAllCallback{

    private static List<Field> findTargetFields(ExtensionContext context, boolean isStatic)  {
        return findFields(context, HelmChartHandle.class, f -> Modifier.isStatic(f.getModifiers()) == isStatic && f.isAnnotationPresent(HelmChart.class));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        for (Field field : findTargetFields(context, true)) {
            setFieldValue(field, null, createHelmChartHandle(field, context));
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        for (Field field : findTargetFields(context, false)) {
            setFieldValue(field, context.getRequiredTestInstance(), createHelmChartHandle(field, context));
        }
    }

    private HelmChartHandle createHelmChartHandle(Field field, ExtensionContext context) {
        return HelmChartHandle.builder()
                .helmClient(getHelmClient(context))
                .resourceLoaderClass(context.getRequiredTestClass())
                .unpackTempDir(workingDirectory(context))
                .fromAnnotation(field.getAnnotation(HelmChart.class))
                .build();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        for (var field : findTargetFields(context, true)) {
            ((HelmChartHandle)getFieldValue(field, null)).close();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        for (var field : findTargetFields(context, false)) {
            ((HelmChartHandle)getFieldValue(field, context.getRequiredTestInstance())).close();
        }
    }
}
