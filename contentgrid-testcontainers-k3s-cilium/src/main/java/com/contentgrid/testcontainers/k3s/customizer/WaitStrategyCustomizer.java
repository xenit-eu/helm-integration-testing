package com.contentgrid.testcontainers.k3s.customizer;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures the {@link WaitStrategy} for the k3s container
 * <p>
 * Multiple customizers can have their own wait strategy, and the container can
 * only be considered ready once all wait strategies have passed
 */
@RequiredArgsConstructor
public class WaitStrategyCustomizer implements K3sContainerCustomizer {
    private final Set<Class<?>> strategiesToSuppress;
    private final Map<Class<?>, WaitStrategy> strategies;

    public WaitStrategyCustomizer() {
        this(
                Set.of(),
                Map.of(
                        // This is the default wait strategy of K3sContainer
                        K3sContainer.class,
                        Wait.forLogMessage(".*Node controller sync successful.*", 1)
                )
        );
    }

    @Override
    public void customize(K3sContainer container) {

        var finalStrategies = strategies.entrySet()
                .stream()
                .filter(entry -> !strategiesToSuppress.contains(entry.getKey()))
                .map(Entry::getValue)
                .toList();

        container.waitingFor(new CompositeWaitStrategy(finalStrategies));
    }

    /**
     * Omit a wait strategy
     * @param source The source of the wait strategy to omit
     */
    public WaitStrategyCustomizer suppressWaitStrategy(Class<?> source) {
        var toOmitCopy = new HashSet<>(strategiesToSuppress);
        toOmitCopy.add(source);
        return new WaitStrategyCustomizer(toOmitCopy, strategies);
    }

    /**
     * Adds an additional wait strategy
     * @param source The source of the wait strategy (typically the class of the caller)
     * @param additionalStrategy The wait strategy to add
     */
    public WaitStrategyCustomizer withAdditionalWaitStrategy(Class<?> source, WaitStrategy additionalStrategy) {
        var strategiesCopy = new LinkedHashMap<>(strategies);
        strategiesCopy.put(source, additionalStrategy);
        return new WaitStrategyCustomizer(strategiesToSuppress, strategiesCopy);
    }

    @RequiredArgsConstructor
    private static class CompositeWaitStrategy implements WaitStrategy {
        private final Collection<WaitStrategy> strategies;

        @Override
        @SneakyThrows({InterruptedException.class, ExecutionException.class})
        public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
            var pool = new ForkJoinPool(Math.min(strategies.size(), 0x7fff));
            try {
                CompletableFuture.allOf(
                        strategies.stream()
                                .map(strategy -> CompletableFuture.runAsync(
                                        () -> strategy.waitUntilReady(waitStrategyTarget), pool))
                                .toArray(CompletableFuture[]::new)
                ).get();
            } finally {
                pool.shutdown();
            }
        }

        @Override
        public WaitStrategy withStartupTimeout(Duration startupTimeout) {
            strategies.forEach(strategy -> strategy.withStartupTimeout(startupTimeout));
            return this;
        }
    }
}
