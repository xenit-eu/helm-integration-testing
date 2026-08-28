package com.contentgrid.testcontainers.k3s.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Quantity;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


class FakeCpuK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {
    @ParameterizedTest
    @ValueSource(ints = {
            1, // Reducing available cores below what this machine has
            120 // Increasing available cores beyond what this machine has
    })
    void changeCpus(int cpuCount) {
        var kubernetesClient = createContainer(customizers -> {
            customizers.configure(FakeCpuK3sContainerCustomizer.class, cpu -> cpu.withNumaNodes(4).withCpuCount(cpuCount));
        });

        assertThat(kubernetesClient.nodes().list().getItems()).singleElement()
                .satisfies(node -> {
                    assertThat(node.getStatus().getCapacity()).extractingByKey("cpu").extracting(Quantity::getNumericalAmount)
                            .isEqualTo(BigDecimal.valueOf(cpuCount));
                });
    }

}