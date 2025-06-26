package com.contentgrid.junit.jupiter.k8s.wait.resource;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.readiness.Readiness;
import java.io.BufferedReader;
import java.io.Reader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PodAwaitableResource extends AbstractAwaitableResource<Pod> {

    public PodAwaitableResource(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, Pod item) {
        super(client, factory, item);
    }

    @Override
    public Stream<LogLine> logs() {
        var resource = client.pods().resource(item);
        var containers = item.getSpec().getContainers()
                .stream()
                .map(Container::getName)
                .toList();
        return containers.stream()
                .flatMap(containerName -> readContainer(resource, containerName));
    }

    private Stream<? extends LogLine> readContainer(PodResource resource, String containerName) {
        Deque<Reader> logReaders = new LinkedList<>();

        // The order here is important: first try to grab the logs of the currently running container
        // And then grab the logs of the terminated container
        // This covers the case where a container is restarted between the two reads
        // To maintain logical ordering of logs, first list those of the terminated container, then of the currently running one

        try {
            logReaders.add(resource.inContainer(containerName)
                    .usingTimestamps()
                    .withPrettyOutput()
                    .getLogReader()
            );
        } catch(KubernetesClientException exception) {
            log.warn("Failed to read logs from {} container {}", this, containerName, exception);
        }

        try {
            logReaders.addFirst(
                    resource.inContainer(containerName)
                            .usingTimestamps()
                            .terminated()
                            .withPrettyOutput()
                            .getLogReader()
            );
        } catch(KubernetesClientException exception) {
            log.warn("Failed to read logs from {} container {}", this, containerName, exception);
        }
        return logReaders.stream().flatMap(logReader -> readLogs(containerName, logReader));
    }

    private Stream<LogLine> readLogs(String containerName, Reader logReader) {
        return new BufferedReader(logReader).lines()
                .map(line -> createLogLine(line, containerName));
    }

    private LogLine createLogLine(String line, String container) {
        var firstSpace = line.indexOf(' ');
        Instant timestamp = null;
        String logLine = line;
        try {
            timestamp = Instant.parse(line.substring(0, firstSpace));
            logLine = line.substring(firstSpace + 1);
        } catch(DateTimeParseException ex) {
            // Timestamp can not be parsed, leave as null and have the full line as logline
        }
        return new LogLine(
                this,
                timestamp,
                container,
                logLine
        );
    }


    @Override
    public boolean isReady() {
        return Readiness.isPodReady(item);
    }
}
