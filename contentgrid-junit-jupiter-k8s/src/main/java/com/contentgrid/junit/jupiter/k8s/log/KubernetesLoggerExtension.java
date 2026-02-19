package com.contentgrid.junit.jupiter.k8s.log;

import static com.contentgrid.junit.jupiter.helpers.FieldHelper.findFields;
import static com.contentgrid.junit.jupiter.helpers.FieldHelper.getFieldValue;

import com.contentgrid.junit.jupiter.helpers.FieldHelper;
import io.fabric8.junit.jupiter.HasKubernetesClient;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@Slf4j
public class KubernetesLoggerExtension implements HasKubernetesClient, BeforeEachCallback, AfterEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        getStore(context).put(KubernetesLoggerExtension.class, Instant.now());
        for (var field : findFields(context, KubernetesLoggerExtension.class, f -> true)) {
            FieldHelper.setFieldValue(field, context.getTestInstance(), createKubernetesLogger(context));
        }
    }

    private KubernetesResourceLogger createKubernetesLogger(ExtensionContext context) {
        return new KubernetesResourceLogger(getClient(context));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if(context.getExecutionException().isEmpty()) {
            return; // No need to write logs when no exception was thrown (so tests did not fail
        }

        var logStartTime = getStore(context).get(KubernetesLoggerExtension.class, Instant.class);

        for (var field : findFields(context, KubernetesLoggerExtension.class, f -> true)) {
            var logger = (KubernetesResourceLogger)getFieldValue(field, context.getRequiredTestInstance());
            if(logger != null) {
                logger.logsSince(logStartTime)
                        .forEachOrdered(line -> log.info("[{}] {} {} >>> {}", line.resource(), line.timestamp(), line.container(), line.line()));
            }
        }

    }

}
