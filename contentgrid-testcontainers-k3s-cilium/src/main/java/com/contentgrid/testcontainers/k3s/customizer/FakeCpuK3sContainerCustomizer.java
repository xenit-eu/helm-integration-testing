package com.contentgrid.testcontainers.k3s.customizer;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;
import org.testcontainers.k3s.K3sContainer;

/**
 * Configures the number of CPUs that are made visible to k3s
 * <p>
 * This only affects the number CPUs and their topology that is reported to k3s,
 * it does not affect the number of actual CPUs that are used to run processes on.
 */
@With
@NoArgsConstructor
@AllArgsConstructor
public class FakeCpuK3sContainerCustomizer implements K3sContainerCustomizer {

    public static final String SYSTEM_NODE = "/sys/devices/system/node";
    public static final String SYSTEM_CPU = "/sys/devices/system/cpu";

    /**
     * The number of NUMA nodes to configure
     */
    private int numaNodes = 1;

    /**
     * The number of CPU cores per NUMA node
     */
    private Integer coresPerNode = null;

    /**
     * The number of hyperthreads per CPU core
     */
    private int threadsPerCore = 2;

    @Value
    @Accessors(fluent = true)
    class CpuNode {
        int numaNode;
        int nodeCpuNum;
        int cpuThreadNum;

        /**
         * Globally unique id of the physical core, numbering cores sequentially across all numa nodes.
         */
        public int coreId() {
            return numaNode * coresPerNode + nodeCpuNum;
        }

        /**
         * Globally unique id of the logical cpu (hardware thread), numbering threads sequentially across all cores.
         */
        public int cpuNum() {
            return coreId() * threadsPerCore + cpuThreadNum;
        }

    }

    /**
     * Configure an exact amount of available CPUs.
     * <p>
     * The {@link #numaNodes}, {@link #coresPerNode} and {@link #threadsPerCore} are automatically adjusted so the total number of CPUs is correct
     */
    public FakeCpuK3sContainerCustomizer withCpuCount(Integer cpus) {
        if (cpus == null) {
            return withCoresPerNode(null);
        }
        var copy = this;
        if (cpus % numaNodes != 0) {
            copy = copy.withNumaNodes(1);
        } else {
            cpus /= numaNodes;
        }
        if (cpus % threadsPerCore != 0) {
            copy = copy.withThreadsPerCore(1);
        } else {
            cpus /= threadsPerCore;
        }
        return copy.withCoresPerNode(cpus);
    }

    private Stream<CpuNode> cpuNodes() {
        return IntStream.range(0, numaNodes)
                .mapToObj(numaNode -> IntStream.range(0, coresPerNode)
                        .mapToObj(numaCpuNum -> IntStream.range(0, threadsPerCore)
                                .mapToObj(cpuThread -> new CpuNode(numaNode, numaCpuNum, cpuThread))
                        ).flatMap(Function.identity())
                ).flatMap(Function.identity());
    }

    @SneakyThrows(IOException.class)
    @Override
    public void customize(K3sContainer container) {
        // Remove existing binds for node topology
        container.withCreateContainerCmdModifier(CustomizerUtils.withBinds(binds -> binds
                .filter(Predicate.not(bind -> Objects.equals(bind.getVolume().getPath(), SYSTEM_NODE) || Objects.equals(bind.getVolume().getPath(), SYSTEM_CPU)))
        ));
        if(coresPerNode == null) {
            return;
        }
        var systemNodeRoot = Files.createTempDirectory("fake-node");
        var systemCpuRoot = Files.createTempDirectory("fake-cpu");

        var maxCoreId = 0;

        // The CPU topology is read from sysfs by cadvisor: https://github.com/google/cadvisor/blob/6a0c4f2539a8b1c11215804dee404c06fe3e0218/lib/utils/sysinfo/sysinfo.go#L204
        for (var cpuNode : cpuNodes().toList()) {
            var topologyDir = systemNodeRoot.resolve("node%d/cpu%d/topology".formatted(cpuNode.numaNode(), cpuNode.cpuNum()));
            Files.createDirectories(topologyDir);
            Files.writeString(topologyDir.resolve("core_id"), cpuNode.coreId() +"\n");
            Files.writeString(topologyDir.resolve("physical_package_id"), "0\n");

            var cpuDir = systemCpuRoot.resolve("cpu%d".formatted(cpuNode.cpuNum()));
            Files.createDirectories(cpuDir);


            maxCoreId = Math.max(maxCoreId, cpuNode.cpuNum());
        }

        Files.writeString(systemCpuRoot.resolve("online"), "0-"+maxCoreId+"\n");

        var systemNodeBind = new Bind(
                systemNodeRoot.toAbsolutePath().toString(),
                new Volume(SYSTEM_NODE),
                AccessMode.ro
        );

        var systemCpuBind = new Bind(
                systemCpuRoot.toAbsolutePath().toString(),
                new Volume(SYSTEM_CPU),
                AccessMode.ro
        );

        container.withCreateContainerCmdModifier(CustomizerUtils.withBinds(binds -> Stream.concat(binds, Stream.of(systemNodeBind, systemCpuBind))));
    }

}
