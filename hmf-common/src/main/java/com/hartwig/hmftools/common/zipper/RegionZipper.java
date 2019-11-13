package com.hartwig.hmftools.common.zipper;

import java.util.List;

import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;

import org.jetbrains.annotations.NotNull;

public final class RegionZipper {

    private RegionZipper() {
    }

    public static <S extends GenomeRegion, T extends GenomeRegion> void zip(List<S> primary, List<T> secondary,
            RegionZipperHandler<S, T> handler) {
        String chromosome = "";

        int i = 0, j = 0;
        while (i < primary.size() || j < secondary.size()) {

            S leftRegion = i < primary.size() ? primary.get(i) : null;
            T rightRegion = j < secondary.size() ? secondary.get(j) : null;

            if (leftRegion == null || (rightRegion != null && compare(leftRegion, rightRegion) > 0)) {
                assert rightRegion != null;
                if (!rightRegion.chromosome().equals(chromosome)) {
                    chromosome = rightRegion.chromosome();
                    handler.enterChromosome(chromosome);
                }
                handler.secondary(rightRegion);
                j++;
            } else {
                if (!leftRegion.chromosome().equals(chromosome)) {
                    chromosome = leftRegion.chromosome();
                    handler.enterChromosome(chromosome);
                }
                handler.primary(leftRegion);
                i++;
            }
        }
    }

    private static int compare(@NotNull GenomeRegion position, @NotNull GenomeRegion region) {
        int positionChromosome = HumanChromosome.fromString(position.chromosome()).intValue();
        int regionChromosome = HumanChromosome.fromString(region.chromosome()).intValue();
        if (positionChromosome < regionChromosome) {
            return -1;
        }

        if (positionChromosome > regionChromosome) {
            return 1;
        }

        return Long.compare(position.start(), region.start());
    }
}
