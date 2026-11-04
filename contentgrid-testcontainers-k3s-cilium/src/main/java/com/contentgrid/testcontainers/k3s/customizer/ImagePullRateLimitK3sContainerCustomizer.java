package com.contentgrid.testcontainers.k3s.customizer;

import java.util.Arrays;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures the rate limit of the kubelet for pulling images.
 * <p>
 * The kubelet limits image pulls to 5 per second (with a burst of 10) by default.
 * Installing a chart with many pods can exceed that limit, causing {@code ErrImagePull} with "pull QPS exceeded"
 * and an exponential backoff before the pull is retried.
 * <p>
 * By default, this customizer disables the limit, as the cluster is only used for testing.
 */
@With
@NoArgsConstructor
@AllArgsConstructor
public class ImagePullRateLimitK3sContainerCustomizer implements K3sContainerCustomizer {

    /**
     * Maximum number of image pulls per second. {@code 0} disables the limit, {@code null} uses the kubelet default.
     */
    private Integer pullsPerSecond = 0;

    @Override
    public void customize(K3sContainer container) {
        if (pullsPerSecond == null) {
            return;
        }
        container.setCommandParts(Stream.concat(
                Arrays.stream(container.getCommandParts()),
                Stream.of("--kubelet-arg=registry-qps=" + pullsPerSecond)
        ).toArray(String[]::new));
    }
}
