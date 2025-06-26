package com.contentgrid.testcontainers.k3s;

import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizersImpl;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizer;
import com.contentgrid.testcontainers.k3s.customizer.K3sContainerCustomizers;
import lombok.experimental.Delegate;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A K3S container that can easily be customized with {@link K3sContainerCustomizer}s
 */
public class CustomizableK3sContainer extends K3sContainer implements K3sContainerCustomizers {

    @Delegate(types = {K3sContainerCustomizers.class})
    private final K3sContainerCustomizersImpl customizers = new K3sContainerCustomizersImpl();

    public CustomizableK3sContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    @Override
    public void start() {
        if(getContainerId() == null) {
            customizers.customize(this);
        }
        super.start();
    }
}
