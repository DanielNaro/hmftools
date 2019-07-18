package com.hartwig.hmftools.common.drivercatalog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;

public class DriverCatalogFile {

    static final DecimalFormat FORMAT = new DecimalFormat("0.0000");
    static final String HEADER_PREFIX = "chromosome";
    private static final String DELIMITER = "\t";
    private static final String DRIVER_CATALOG_EXTENSION = ".driver.catalog.tsv";

    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample) {
        return basePath + File.separator + sample + DRIVER_CATALOG_EXTENSION;
    }

    @NotNull
    public static List<DriverCatalog> read(@NotNull final String fileName) throws IOException {
        return fromLines(Files.readAllLines(new File(fileName).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull final List<DriverCatalog> catalog) throws IOException {
        Files.write(new File(filename).toPath(), toLines(catalog));
    }

    @NotNull
    static List<String> toLines(@NotNull final List<DriverCatalog> catalog) {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        catalog.stream().map(DriverCatalogFile::toString).forEach(lines::add);
        return lines;
    }

    @NotNull
     static List<DriverCatalog> fromLines(@NotNull final List<String> lines) {
        final List<DriverCatalog> result = Lists.newArrayList();
        for (String line : lines) {
            if (!line.startsWith(HEADER_PREFIX)) {
                result.add(fromString(line));
            }
        }
        return result;
    }

    @NotNull
    private static String header() {
        return new StringJoiner(DELIMITER, "", "")
                .add("chromosome")
                .add("chromosomeBand")
                .add("gene")
                .add("driver")
                .add("category")
                .add("likelihoodMethod")
                .add("driverLikelihood")
                .add("dndsLikelihood")
                .add("missense")
                .add("nonsense")
                .add("splice")
                .add("inframe")
                .add("frameshift")
                .add("biallelic")
                .add("minCopyNumber")
                .add("maxCopyNumber")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final DriverCatalog ratio) {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(ratio.chromosome()))
                .add(String.valueOf(ratio.chromosomeBand()))
                .add(String.valueOf(ratio.gene()))
                .add(String.valueOf(ratio.driver()))
                .add(String.valueOf(ratio.category()))
                .add(String.valueOf(ratio.likelihoodMethod()))
                .add(FORMAT.format(ratio.driverLikelihood()))
                .add(FORMAT.format(ratio.dndsLikelihood()))
                .add(String.valueOf(ratio.missense()))
                .add(String.valueOf(ratio.nonsense()))
                .add(String.valueOf(ratio.splice()))
                .add(String.valueOf(ratio.inframe()))
                .add(String.valueOf(ratio.frameshift()))
                .add(String.valueOf(ratio.biallelic()))
                .add(String.valueOf(ratio.minCopyNumber()))
                .add(String.valueOf(ratio.maxCopyNumber()))
                .toString();
    }

    @NotNull
    private static DriverCatalog fromString(@NotNull final String line) {
        String[] values = line.split(DELIMITER);
        ImmutableDriverCatalog.Builder builder = ImmutableDriverCatalog.builder()
                .chromosome(values[0])
                .chromosomeBand(values[1])
                .gene(values[2])
                .driver(DriverType.valueOf(values[3]))
                .category(DriverCategory.valueOf(values[4]))
                .likelihoodMethod(LikelihoodMethod.valueOf(values[5]))
                .driverLikelihood(Double.valueOf(values[6]))
                .dndsLikelihood(Double.valueOf(values[7]))
                .missense(Long.valueOf(values[8]))
                .nonsense(Long.valueOf(values[9]))
                .splice(Long.valueOf(values[10]))
                .inframe(Long.valueOf(values[11]))
                .frameshift(Long.valueOf(values[12]))
                .biallelic(Boolean.valueOf(values[13]))
                .minCopyNumber(Double.valueOf(values[14]))
                .maxCopyNumber(Double.valueOf(values[15]));

        return builder.build();
    }
}
