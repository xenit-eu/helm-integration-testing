package com.contentgrid.testcontainers.k3s.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.testcontainers.k3s.customizer.MemoryLimitK3sContainerCustomizer.SizeUnit;
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
    void changeMemory(int value, SizeUnit unit) {
        var kubernetesClient = createContainer(customizers -> {
            customizers.configure(MemoryLimitK3sContainerCustomizer.class, mem -> mem.withAvailableMemory(value, unit));
        });

        assertThat(kubernetesClient.nodes().list().getItems()).singleElement()
                .satisfies(node -> {
                    assertThat(node.getStatus().getCapacity()).extractingByKey("memory").extracting(Quantity::getAmountInBytes)
                            .isEqualTo(BigDecimal.valueOf(unit.of(value)));
                });
    }

    @ParameterizedTest
    @CsvSource({
        "5.1,GiB,5476083302",
        "1,MiB,1048576",
        "1.2,KiB,1229",
    })
    void unitConversion(double value, SizeUnit unit, long bytes) {
        assertThat(unit.of(value)).isEqualTo(bytes);
    }

}