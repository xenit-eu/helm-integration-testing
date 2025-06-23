package com.contentgrid.testcontainers.k3s;

import com.contentgrid.testcontainers.k3s.customizer.FreezableK3sContainerCustomizersImpl;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import java.util.List;
import lombok.experimental.Delegate;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A K3S container that can easily be customized with {@link K3sContainerCustomizer}s
 */
public class CustomizableK3sContainer extends K3sContainer implements K3sContainerCustomizers {

    @Delegate(types = {K3sContainerCustomizers.class})
    private final FreezableK3sContainerCustomizersImpl customizers = new FreezableK3sContainerCustomizersImpl();

    public CustomizableK3sContainer(DockerImageName dockerImageName) {
        this(dockerImageName, List.of());
    }

    public CustomizableK3sContainer(DockerImageName dockerImageName, Iterable<K3sContainerCustomizer> customizers) {
        super(dockerImageName);
        this.customize(customizers);
    }

    @Override
    public void start() {
        if(getContainerId() != null) {
            customizers.customize(this);
            customizers.freeze();
        }
        super.start();
    }
}
