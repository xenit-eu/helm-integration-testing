package com.contentgrid.testcontainers.k3s.customizer;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;

@Slf4j
@RequiredArgsConstructor
public class LoggingK3sContainerCustomizer implements K3sContainerCustomizer {
    @With
    @NonNull
    private final Logger logger;

    public LoggingK3sContainerCustomizer() {
        this(log);
    }

    @Override
    public void customize(K3sContainer container) {
        container.withLogConsumer(new Slf4jLogConsumer(logger, true));
    }
}
