package com.contentgrid.junit.jupiter.k8s.providers;

import com.contentgrid.testcontainers.k3s.K3sCiliumContainer;

public class K3sCiliumDefaultDenyCoreDNSClusterProvider extends K3sTestcontainersClusterProvider {

    public K3sCiliumDefaultDenyCoreDNSClusterProvider() {
        super(new K3sCiliumContainer(K3sTestcontainersClusterProvider.IMAGE_RANCHER_K3S, true, true));
    }

}
