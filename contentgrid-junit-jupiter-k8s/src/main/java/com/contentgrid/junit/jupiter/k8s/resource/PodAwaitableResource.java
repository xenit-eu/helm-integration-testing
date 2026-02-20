package com.contentgrid.junit.jupiter.k8s.resource;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.readiness.Readiness;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class PodAwaitableResource extends AbstractAwaitableResource<Pod> {

    public PodAwaitableResource(@NonNull KubernetesClient client,
            @NonNull AwaitableResourceFactory factory, Pod item) {
        super(client, factory, item);
    }

    @Override
    public Stream<LogLine> logs() {
        var resource = client.pods().inNamespace(item.getMetadata().getNamespace()).resource(item);
        var containers = item.getSpec().getContainers()
                .stream()
                .map(Container::getName)
                .toList();
        return containers.stream()
                .flatMap(containerName -> readContainer(resource, containerName));
    }

    private Stream<LogLine> readContainer(PodResource resource, String containerName) {
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
            logReadException(containerName, exception);
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
            logReadException(containerName, exception);
        }
        return logReaders.stream().flatMap(logReader -> readLogs(containerName, logReader));
    }

    private void logReadException(String containerName, KubernetesClientException exception) {
        if(log.isTraceEnabled()) {
            log.warn("Failed to read logs from {} container {}", this, containerName, exception);
        } else {
            log.warn("Failed to read logs from {} container {}: {}", this, containerName, exception.getStatus().getMessage());
        }
    }

    private Stream<LogLine> readLogs(String containerName, Reader logReader) {
        var reader = new BufferedReader(logReader);
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new LogReaderIterator(reader, containerName),
                        Spliterator.ORDERED|Spliterator.NONNULL
                ),
                false
        );
    }


    @Override
    public boolean isReady() {
        return Readiness.isPodReady(item);
    }

    @RequiredArgsConstructor
    private class LogReaderIterator implements Iterator<LogLine> {

        @NonNull
        private final BufferedReader reader;
        @NonNull
        private final String containerName;
        private LogLine nextLine = null;
        private Instant prevTimestamp = Instant.EPOCH;

        @Override
        public boolean hasNext() {
            if(nextLine != null) {
                return true;
            } else {
                try {
                    var read = reader.readLine();
                    if (read == null) {
                        return false;
                    }
                    nextLine = createLogLine(read, containerName, prevTimestamp);
                    prevTimestamp = nextLine.timestamp();
                    return true;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        private LogLine createLogLine(String line, String container, Instant fallbackTimestamp) {
            var firstSpace = line.indexOf(' ');
            Instant timestamp = fallbackTimestamp;
            String logLine = line;
            try {
                timestamp = Instant.parse(line.substring(0, firstSpace));
                logLine = line.substring(firstSpace + 1);
            } catch(DateTimeParseException ex) {
                // Timestamp can not be parsed, leave as the fallback and have the full line as logline
                // In some cases, a log line doesn't have a timestamp.
                // That appears to happen when a line is 'rewritten', typically by a CLI tool that thinks it's writing to a TTY.
                // A rewritten line doesn't have a newline, it only has a carriage return (to reset the cursor to te beginning of the line),
                // and then writes the same line again.
            }
            return new LogLine(
                    PodAwaitableResource.this,
                    timestamp,
                    container,
                    logLine
            );
        }

        @Override
        public LogLine next() {
            if(nextLine != null || hasNext()) {
                var line = nextLine;
                nextLine = null;
                return line;
            } else {
                throw new NoSuchElementException();
            }
        }
    }
}
