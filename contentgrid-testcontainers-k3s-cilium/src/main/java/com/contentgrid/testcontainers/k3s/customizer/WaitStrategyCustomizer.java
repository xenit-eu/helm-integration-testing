package com.contentgrid.testcontainers.k3s.customizer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private final WaitStrategy waitStrategy;

    public WaitStrategyCustomizer() {
        // This is the default wait strategy of K3sContainer
        this(Wait.forLogMessage(".*Node controller sync successful.*", 1));
    }

    @Override
    public void customize(K3sContainer container) {
        container.waitingFor(waitStrategy);
    }

    public WaitStrategyCustomizer withAdditionalWaitStrategy(WaitStrategy additionalStrategy) {
        return new WaitStrategyCustomizer(CompositeWaitStrategy.from(this.waitStrategy, additionalStrategy));
    }

    @RequiredArgsConstructor
    private static class CompositeWaitStrategy implements WaitStrategy {
        private final List<WaitStrategy> strategies;

        public static WaitStrategy from(WaitStrategy waitStrategy, WaitStrategy additionalStrategy) {
            var strategies = new ArrayList<WaitStrategy>();
            strategies.addAll(allStrategies(waitStrategy));
            strategies.addAll(allStrategies(additionalStrategy));
            return new CompositeWaitStrategy(strategies);
        }

        private static List<WaitStrategy> allStrategies(WaitStrategy strategy) {
            if (strategy instanceof CompositeWaitStrategy compositeWaitStrategy) {
                return compositeWaitStrategy.strategies;
            }
            return List.of(strategy);
        }

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
