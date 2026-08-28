package com.contentgrid.testcontainers.k3s.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Quantity;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


class MemoryLimitK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @ParameterizedTest
    @CsvSource({
            "500,MiB", // Less than what's available in this machine, but enough so kubernetes can actually start
            "120,GiB" // More than what's available in this machine
    })
    void changeMemory(int value, MemoryLimitK3sContainerCustomizer.SizeUnit unit) {
        var kubernetesClient = createContainer(customizers -> {
            customizers.configure(MemoryLimitK3sContainerCustomizer.class, mem -> mem.withAvailableMemory(value, unit));
        });

        assertThat(kubernetesClient.nodes().list().getItems()).singleElement()
                .satisfies(node -> {
                    assertThat(node.getStatus().getCapacity()).extractingByKey("memory").extracting(Quantity::getAmountInBytes)
                            .isEqualTo(BigDecimal.valueOf(unit.of(value)));
                });
    }

}