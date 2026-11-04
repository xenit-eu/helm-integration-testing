package com.contentgrid.testcontainers.k3s.customizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImagePullRateLimitK3sContainerCustomizerTest extends AbstractK3sContainerCustomizerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledByDefault() throws JsonProcessingException {
        assertThat(registryPullQps(customizers -> {})).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "3,3",
            ",5", // null keeps the kubelet default
    })
    void configuredRateLimit(Integer pullsPerSecond, int expectedQps) throws JsonProcessingException {
        assertThat(registryPullQps(customizers -> {
            customizers.configure(ImagePullRateLimitK3sContainerCustomizer.class, limit -> limit.withPullsPerSecond(pullsPerSecond));
        })).isEqualTo(expectedQps);
    }

    /**
     * Reads the registry pull rate limit from the running configuration of the kubelet
     */
    private int registryPullQps(Consumer<K3sContainerCustomizers> configuration) throws JsonProcessingException {
        KubernetesClient client = createContainer(configuration);
        var nodeName = client.nodes().list().getItems().get(0).getMetadata().getName();

        var configz = client.raw("/api/v1/nodes/%s/proxy/configz".formatted(nodeName));

        return objectMapper.readTree(configz).path("kubeletconfig").path("registryPullQPS").asInt(-1);
    }
}
