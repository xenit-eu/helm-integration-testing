package com.contentgrid.helm.impl;

import com.contentgrid.helm.HelmClient;
import com.contentgrid.helm.HelmTemplateCommand.TemplateFlag;
import java.io.IOException;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class HelmTemplateCommandTest {

    @Test
    void cilium() {

        var helm = HelmClient.builder().build();

        var result = helm.template()
                .chart("cilium", "cilium",
                        TemplateFlag.repo("https://helm.cilium.io/"),
                        TemplateFlag.namespace("cilium"),
                        TemplateFlag.values(Map.of(
                                "operator.replicas", 1
                        )),
                        TemplateFlag.version("1.14.6")
                );

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getOutput()).isNotEmpty();
        Assertions.assertThat(result.getOutput()).contains("""
                # Source: cilium/templates/cilium-operator/deployment.yaml
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: cilium-operator
                  namespace: cilium
                """);
    }
}