package com.hartwig.hmftools.common.pcf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.position.GenomePosition;
import com.hartwig.hmftools.common.region.GenomeRegion;

import org.jetbrains.annotations.NotNull;

public class PCFFile {

    private static final String DELIMITER = "\t";
    private static final String HEADER_PREFIX = "sampleID";
    private static final String RATIO_EXTENSION = ".cobalt.ratio.pcf";
    private static final String BAF_EXTENSION = ".amber.baf.pcf";

    @NotNull
    public static String generateRatioFilename(@NotNull final String basePath, @NotNull final String sample) {
        return basePath + File.separator + sample + RATIO_EXTENSION;
    }

    @NotNull
    public static String generateBAFFilename(@NotNull final String basePath, @NotNull final String sample) {
        return basePath + File.separator + sample + BAF_EXTENSION;
    }

    public static Multimap<String, GenomePosition> readPositions(int windowSize, @NotNull final String filename) throws IOException {
        Multimap<String, GenomePosition> result = ArrayListMultimap.create();
        long end = 0;
        for (String line : Files.readAllLines(new File(filename).toPath())) {
            if (!line.startsWith(HEADER_PREFIX)) {
                String[] values = line.split(DELIMITER);
                final String chromosomeName = values[1];
                long start = Long.valueOf(values[3]);
                if (start != end) {
                    result.put(chromosomeName, position(chromosomeName, start));
                }
                end = Long.valueOf(values[4]) + windowSize;
                result.put(chromosomeName, position(chromosomeName, end));
            }
        }

        return result;
    }

    @NotNull
    public static Multimap<String, GenomeRegion> read(int windowSize, @NotNull final String filename) throws IOException {
        return fromLines(windowSize, Files.readAllLines(new File(filename).toPath()));
    }

    @NotNull
    private static Multimap<String, GenomeRegion> fromLines(int windowSize, @NotNull List<String> lines) {
        Multimap<String, GenomeRegion> result = ArrayListMultimap.create();
        for (String line : lines) {
            if (!line.startsWith(HEADER_PREFIX)) {
                final PCFRegion region = fromString(windowSize, line);
                result.put(region.chromosome(), region);
            }
        }
        return result;
    }

    @NotNull
    private static PCFRegion fromString(int windowSize, @NotNull final String line) {
        String[] values = line.split(DELIMITER);
        return ImmutablePCFRegion.builder()
                .chromosome(values[1])
                .start(Long.valueOf(values[3]))
                .end(Long.valueOf(values[4]) + windowSize - 1)
                .build();
    }

    private static PCFPosition position(@NotNull final String chromosome, long pos) {
        return ImmutablePCFPosition.builder().chromosome(chromosome).position(pos).build();
    }
}
