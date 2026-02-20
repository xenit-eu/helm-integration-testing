package com.contentgrid.junit.jupiter.k8s.log;

import static com.contentgrid.junit.jupiter.helpers.FieldHelper.findFields;
import static com.contentgrid.junit.jupiter.helpers.FieldHelper.getFieldValue;

import com.contentgrid.junit.jupiter.helm.HelmChart;
import com.contentgrid.junit.jupiter.helm.HelmChartHandle;
import com.contentgrid.junit.jupiter.helpers.FieldHelper;
import io.fabric8.junit.jupiter.HasKubernetesClient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;

@Slf4j
public class KubernetesLoggerExtension implements HasKubernetesClient, BeforeAllCallback, BeforeEachCallback, AfterEachCallback,
        LifecycleMethodExecutionExceptionHandler {
    private static List<Field> findTargetFields(ExtensionContext context, boolean isStatic)  {
        return findFields(context, KubernetesResourceLogger.class, f -> Modifier.isStatic(f.getModifiers()) == isStatic);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        for (var field : findTargetFields(context, true)) {
            FieldHelper.setFieldValue(field, context.getTestInstance(), createKubernetesLogger(context));
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        for (var field : findTargetFields(context, false)) {
            FieldHelper.setFieldValue(field, context.getTestInstance(), createKubernetesLogger(context));
        }
    }

    private KubernetesResourceLogger createKubernetesLogger(ExtensionContext context) {
        return new KubernetesResourceLogger(getClient(context));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if(context.getExecutionException().isEmpty()) {
            return; // No need to write logs when no exception was thrown (so tests did not fail)
        }

        dumpLogs(context);

    }

    private void dumpLogs(ExtensionContext context) throws IllegalAccessException {
        for (var field : findFields(context, KubernetesResourceLogger.class, f -> true)) {
            var logger = (KubernetesResourceLogger)getFieldValue(field, context.getRequiredTestInstance());
            if(logger != null) {
                logger.logs()
                        .forEachOrdered(line -> log.info("[{}] {} {} >>> {}", line.resource(), line.timestamp(), line.container(), line.line()));
            }
        }
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        dumpLogs(context);
        throw throwable;
    }

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        dumpLogs(context);
        throw throwable;
    }

}
