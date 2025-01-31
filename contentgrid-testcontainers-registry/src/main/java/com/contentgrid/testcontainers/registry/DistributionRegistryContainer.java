package com.contentgrid.testcontainers.registry;

import com.contentgrid.testcontainers.registry.DistributionRegistryContainer.DistributionConfiguration.ProxyConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class DistributionRegistryContainer extends GenericContainer<DistributionRegistryContainer>  {

    public static final DockerImageName IMAGE_NAME = DockerImageName.parse("docker.io/registry:2");

    private final DistributionConfiguration configuration = new DistributionConfiguration();

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .enable(Feature.MINIMIZE_QUOTES)
    ).setSerializationInclusion(Include.NON_NULL);

    public static int HTTP_PORT = 5000;

    public DistributionRegistryContainer() {
        this(IMAGE_NAME);
    }

    public DistributionRegistryContainer(DockerImageName dockerImageName) {
        super(dockerImageName);

        dockerImageName.assertCompatibleWith(IMAGE_NAME);
        addExposedPorts(HTTP_PORT);

    }

    public DistributionRegistryContainer withProxy(String remoteUrl) {
        return this.withProxy(remoteUrl, null, null);
    }
    public DistributionRegistryContainer withProxy(String remoteUrl, String username, String password) {
        this.configuration.setProxy(new ProxyConfiguration(remoteUrl, username, password, null));
        return this;
    }

    @Override
    protected void configure() {
        withCopyToContainer(Transferable.of(registryConfig()), "/etc/docker/registry/config.yml");
    }

    @SneakyThrows
    String registryConfig() {
        return this.yamlMapper.writeValueAsString(this.configuration);
    }

    public String getRegistry() {
        return this.getHost() + ":" + this.getMappedPort(HTTP_PORT);
    }


    @Data
    static class DistributionConfiguration {

        String version = "0.1";
        Map<String, Object> log = Map.of("level", "info");
        Map<String, String> http = Map.of("addr", ":5000");
        Map<String, Object> storage = Map.of("filesystem", Map.of("rootdirectory", "/var/lib/registry"));
        ProxyConfiguration proxy = null;

        @Data
        @AllArgsConstructor
        static class ProxyConfiguration {

            @NonNull
            String remoteurl;

            String username;
            String password;
            String ttl;
        }
    }
}
