package com.contentgrid.testcontainers.k3s.customizer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.testcontainers.utility.MountableFile;

@UtilityClass
public class CustomizerUtils {
    public MountableFile forClassResource(Class<?> clazz, String resource) {
        var file = clazz.getResource(resource).toExternalForm();
        if(file.startsWith("jar:file:")) {
            var exclIndex = file.indexOf('!');
            return MountableFile.forClasspathResource(file.substring(exclIndex + 1));
        } else if (file.startsWith("file:")){
            return MountableFile.forHostPath(file.substring(5));
        }
        throw new IllegalArgumentException("Can not create mountable file for resource '%s'".formatted(file));
    }

    public Consumer<CreateContainerCmd> withHostConfig(UnaryOperator<HostConfig> configurer) {
        return cmd -> cmd.withHostConfig(configurer.apply(cmd.getHostConfig()));
    }

    public Consumer<CreateContainerCmd> withBinds(UnaryOperator<Stream<Bind>> configurer) {
        return withHostConfig(hc -> hc.withBinds(configurer.apply(Arrays.stream(hc.getBinds())).toArray(Bind[]::new)));
    }
}
