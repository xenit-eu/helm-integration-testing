package com.contentgrid.testcontainers.k3s.customizer;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures the amount of memory that is allocated to the container.
 * <p>
 * To ensure that k3s also correctly knows about the limits, {@code /proc/meminfo} is also
 * replaced with a fake copy that mirrors the configured limit.
 */
@With
@NoArgsConstructor
@AllArgsConstructor
public class MemoryLimitK3sContainerCustomizer implements K3sContainerCustomizer {

    public static final String PROC_MEMINFO = "/proc/meminfo";
    public static final String SYSTEM_EDAC = "/sys/devices/system/edac";
    /**
     * Amount of memory to fake to be available (in bytes)
     */
    private Long availableMemoryBytes;

    /**
     * Configure an amount of memory available
     *
     * @param value The amount of memory, expressed in {@code sizeUnit}s
     * @param sizeUnit The size unit for the amount of memory
     */
    public MemoryLimitK3sContainerCustomizer withAvailableMemory(long value, SizeUnit sizeUnit) {
        return withAvailableMemoryBytes(sizeUnit.of(value));
    }

    /**
     * Configure an amount of memory available
     * @param value The amount of memory, expressed in {@code sizeUnit}s
     * @param sizeUnit The size unit for the amount of memory
     */
    public MemoryLimitK3sContainerCustomizer withAvailableMemory(double value, SizeUnit sizeUnit) {
        return withAvailableMemoryBytes(sizeUnit.of(value));
    }

    public enum SizeUnit {
        B(1L),
        KiB(B),
        MiB(KiB),
        GiB(MiB)
        ;

        private final long multiplier;

        SizeUnit(long multiplier) {
            this.multiplier = multiplier;
        }

        SizeUnit(SizeUnit prevUnit) {
            this.multiplier = prevUnit.multiplier * 1024L;
        }

        public long of(double value) {
            return Math.round(value * multiplier);
        }

        public long of(long value) {
            return value * multiplier;
        }
    }

    @Override
    @SneakyThrows(IOException.class)
    public void customize(K3sContainer container) {
        // Configure available memory for the container.
        // This enforces the memory limit
        container.withCreateContainerCmdModifier(CustomizerUtils.withHostConfig(hc -> hc.withMemory(availableMemoryBytes)));

        // Remove existing binds for meminfo and edac
        container.withCreateContainerCmdModifier(CustomizerUtils.withBinds(binds -> binds
                .filter(Predicate.not(
                        bind -> Objects.equals(bind.getVolume().getPath(), PROC_MEMINFO) || Objects.equals(bind.getVolume().getPath(),
                                SYSTEM_EDAC)))
        ));
        if (availableMemoryBytes == null) {
            return;
        }
        var tempFile = Files.createTempFile("fake-limit", ".meminfo");
        tempFile.toFile().deleteOnExit();
        // See also how k3d fakes this: https://github.com/k3d-io/k3d/blob/46f3480daa747ba71c155d0f619ce6aba7b95db9/pkg/util/infofaker.go#L44-L51
        Files.writeString(tempFile,
                String.format(Locale.ROOT, "MemTotal: %d kB\nSwapTotal: 0 kB\n", availableMemoryBytes / SizeUnit.KiB.multiplier));
        var meminfoBind = new Bind(
                tempFile.toAbsolutePath().toString(),
                new Volume(PROC_MEMINFO),
                AccessMode.ro
        );

        var emptyDir = Files.createTempDirectory("fake-limit");
        emptyDir.toFile().deleteOnExit();
        var edacBind = new Bind(
                emptyDir.toAbsolutePath().toString(),
                new Volume(SYSTEM_EDAC),
                AccessMode.ro
        );
        container.withCreateContainerCmdModifier(CustomizerUtils.withBinds(binds -> Stream.concat(
                binds,
                Stream.of(meminfoBind, edacBind)
        )));
    }

}
