package com.hartwig.hmftools.common.purple.gene;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.region.HmfTranscriptRegion;
import com.hartwig.hmftools.common.zipper.RegionZipper;

import org.jetbrains.annotations.NotNull;

public final class GeneCopyNumberFactory {

    private GeneCopyNumberFactory() {
    }

    @NotNull
    public static List<GeneCopyNumber> geneCopyNumbers(@NotNull final List<HmfTranscriptRegion> genes,
            @NotNull final List<PurpleCopyNumber> somaticCopyNumbers, @NotNull final List<PurpleCopyNumber> germlineDeletions) {

        final List<GeneCopyNumber> result = Lists.newArrayList();
        for (HmfTranscriptRegion gene : genes) {
            final GeneCopyNumberBuilder builder = new GeneCopyNumberBuilder(gene);
            RegionZipper.zip(somaticCopyNumbers, gene.exome(), builder);
            RegionZipper.zip(germlineDeletions, gene.exome(), builder);

            GeneCopyNumber geneCopyNumber = builder.build();
            if (geneCopyNumber.totalRegions() > 0) {
                result.add(geneCopyNumber);
            }

        }
        return result;
    }
}
