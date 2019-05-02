package com.hartwig.hmftools.common.variant;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.chromosome.Chromosome;
import com.hartwig.hmftools.common.collect.Multimaps;
import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.GermlineStatus;
import com.hartwig.hmftools.common.region.GenomeRegionSelector;
import com.hartwig.hmftools.common.region.GenomeRegionSelectorFactory;

import org.jetbrains.annotations.NotNull;

@Deprecated
public class PurityAdjustedSomaticVariantFactory {

    @NotNull
    private final PurityAdjuster purityAdjuster;
    @NotNull
    private final GenomeRegionSelector<PurpleCopyNumber> copyNumberSelector;
    @NotNull
    private final GenomeRegionSelector<FittedRegion> fittedRegionSelector;

    public PurityAdjustedSomaticVariantFactory(@NotNull PurityAdjuster purityAdjuster, @NotNull final List<PurpleCopyNumber> copyNumbers,
            @NotNull final List<FittedRegion> fittedRegions) {
        this(purityAdjuster, Multimaps.fromRegions(copyNumbers), Multimaps.fromRegions(fittedRegions));
    }

    public PurityAdjustedSomaticVariantFactory(@NotNull PurityAdjuster purityAdjuster,
            @NotNull final Multimap<Chromosome, PurpleCopyNumber> copyNumbers, @NotNull final Multimap<Chromosome, FittedRegion> fittedRegions) {
        this.purityAdjuster = purityAdjuster;
        this.copyNumberSelector = GenomeRegionSelectorFactory.createImproved(copyNumbers);
        this.fittedRegionSelector = GenomeRegionSelectorFactory.createImproved(fittedRegions);
    }

    @NotNull
    public List<PurityAdjustedSomaticVariant> create(@NotNull final List<SomaticVariant> variants) {
        final List<PurityAdjustedSomaticVariant> result = Lists.newArrayList();

        for (SomaticVariant variant : variants) {
            final ImmutablePurityAdjustedSomaticVariantImpl.Builder builder = ImmutablePurityAdjustedSomaticVariantImpl.builder()
                    .from(variant)
                    .adjustedCopyNumber(0)
                    .adjustedVAF(0)
                    .minorAllelePloidy(0)
                    .germlineStatus(GermlineStatus.UNKNOWN);

            copyNumberSelector.select(variant).ifPresent(x -> purityAdjustment(x, variant, builder));
            fittedRegionSelector.select(variant).ifPresent(x -> builder.germlineStatus(x.status()));
            result.add(builder.build());
        }

        return result;
    }

    private void purityAdjustment(@NotNull final PurpleCopyNumber copyNumber, @NotNull final AllelicDepth depth,
            @NotNull final ImmutablePurityAdjustedSomaticVariantImpl.Builder builder) {
        double adjustedCopyNumber = copyNumber.averageTumorCopyNumber();
        builder.adjustedCopyNumber(adjustedCopyNumber);
        double adjustedVAF =
                purityAdjuster.purityAdjustedVAF(copyNumber.chromosome(), Math.max(0.001, adjustedCopyNumber), depth.alleleFrequency());
        double ploidy = Math.max(0, adjustedVAF * adjustedCopyNumber);

        builder.adjustedVAF(adjustedVAF);
        builder.ploidy(ploidy);

        builder.minorAllelePloidy(copyNumber.minorAllelePloidy());
    }
}
