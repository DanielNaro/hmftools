package com.hartwig.hmftools.common.variant.structural.linx;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;

public class LinxClusterFile
{
    public static final String DELIMITER = "\t";

    private static final String FILE_EXTENSION = ".linx.clusters.tsv";

    @NotNull
    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample)
    {
        return basePath + File.separator + sample + FILE_EXTENSION;
    }

    @NotNull
    public static List<LinxCluster> read(final String filePath) throws IOException
    {
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull List<LinxCluster> clusters) throws IOException
    {
        Files.write(new File(filename).toPath(), toLines(clusters));
    }

    @NotNull
    static List<String> toLines(@NotNull final List<LinxCluster> clusters)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        clusters.stream().map(LinxClusterFile::toString).forEach(lines::add);
        return lines;
    }

    @NotNull
    static List<LinxCluster> fromLines(@NotNull List<String> lines)
    {
        return lines.stream().filter(x -> !x.startsWith("clusterId")).map(LinxClusterFile::fromString).collect(toList());
    }

    @NotNull
    private static String header()
    {
        return new StringJoiner(DELIMITER)
                .add("clusterId")
                .add("resolvedType")
                .add("synthetic")
                .add("subClonal")
                .add("subType")
                .add("clusterCount")
                .add("clusterDesc")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final LinxCluster cluster)
    {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(cluster.clusterId()))
                .add(String.valueOf(cluster.resolvedType()))
                .add(String.valueOf(cluster.synthetic()))
                .add(String.valueOf(cluster.subClonal()))
                .add(String.valueOf(cluster.subType()))
                .add(String.valueOf(cluster.clusterCount()))
                .add(String.valueOf(cluster.clusterDesc()))
                .toString();
    }

    @NotNull
    private static LinxCluster fromString(@NotNull final String clusterData)
    {
        String[] values = clusterData.split(DELIMITER);

        int index = 0;

        return ImmutableLinxCluster.builder()
                .clusterId(Integer.valueOf(values[index++]))
                .resolvedType(values[index++])
                .synthetic(Boolean.valueOf(values[index++]))
                .subClonal(Boolean.valueOf(values[index++]))
                .subType(values[index++])
                .clusterCount(Integer.valueOf(values[index++]))
                .clusterDesc(values[index++])
                .build();
    }
}
