package com.contentgrid.testcontainers.k3s.customizer;

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

}
