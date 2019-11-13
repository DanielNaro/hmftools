package com.hartwig.hmftools.common.purple.purity;

import static com.hartwig.hmftools.common.math.Doubles.greaterOrEqual;
import static com.hartwig.hmftools.common.math.Doubles.lessOrEqual;
import static com.hartwig.hmftools.common.math.Doubles.positiveOrZero;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.position.GenomePositionSelector;
import com.hartwig.hmftools.common.genome.position.GenomePositionSelectorFactory;
import com.hartwig.hmftools.common.math.Doubles;
import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.FittedRegionFactory;
import com.hartwig.hmftools.common.purple.region.GermlineStatus;
import com.hartwig.hmftools.common.purple.region.ObservedRegion;
import com.hartwig.hmftools.common.utils.collection.Downsample;
import com.hartwig.hmftools.common.variant.SomaticVariant;

import org.jetbrains.annotations.NotNull;

public class FittedPurityFactory {

    private static final int MAX_SOMATICS_TO_FIT = 1000;
    private static final double MAX_TUMOR_RATIO_TO_FIT = 3;

    private final int maxPloidy;
    private final Gender gender;
    private final double minPurity;
    private final double maxPurity;
    private final int totalBAFCount;
    private final double purityIncrements;
    private final double normFactorIncrements;
    private final double minNormFactor;
    private final double maxNormFactor;
    private final double somaticPenaltyWeight;

    @NotNull
    private final FittedRegionFactory fittedRegionFactory;
    private final ExecutorService executorService;
    private final Collection<SomaticVariant> variants;

    private final List<FittedPurity> all = Lists.newArrayList();
    private final List<FittedPurity> bestScoringPerPurity = Lists.newArrayList();
    private final List<ObservedRegion> filteredRegions = Lists.newArrayList();

    public FittedPurityFactory(final ExecutorService executorService, final Gender gender, final int maxPloidy, final double minPurity,
            final double maxPurity, final double purityIncrements, final double minNormFactor, final double maxNormFactor,
            final double normFactorIncrements, final double somaticPenaltyWeight, @NotNull final FittedRegionFactory fittedRegionFactory,
            @NotNull final Collection<ObservedRegion> observedRegions, @NotNull final Collection<SomaticVariant> variants)
            throws ExecutionException, InterruptedException {
        this.executorService = executorService;
        this.maxPloidy = maxPloidy;
        this.minPurity = minPurity;
        this.maxPurity = maxPurity;
        this.purityIncrements = purityIncrements;
        this.minNormFactor = minNormFactor;
        this.maxNormFactor = maxNormFactor;
        this.normFactorIncrements = normFactorIncrements;
        this.somaticPenaltyWeight = somaticPenaltyWeight;
        this.fittedRegionFactory = fittedRegionFactory;
        this.gender = gender;

        final List<SomaticVariant> filteredVariants = Lists.newArrayList();
        final GenomePositionSelector<SomaticVariant> variantSelector = GenomePositionSelectorFactory.create(variants);

        for (final ObservedRegion region : observedRegions) {
            final Chromosome chromosome = HumanChromosome.valueOf(region);

            if (region.bafCount() > 0 && positiveOrZero(region.observedTumorRatio()) && chromosome.isAutosome()
                    && region.status() == GermlineStatus.DIPLOID && Doubles.lessOrEqual(region.observedTumorRatio(),
                    MAX_TUMOR_RATIO_TO_FIT)) {
                filteredRegions.add(region);
                variantSelector.select(region, filteredVariants::add);
            }
        }
        this.variants = Downsample.downsample(MAX_SOMATICS_TO_FIT, filteredVariants);
        this.totalBAFCount = filteredRegions.stream().mapToInt(ObservedRegion::bafCount).sum();

        fitPurity();
    }

    public List<FittedPurity> bestFitPerPurity() {
        return bestScoringPerPurity;
    }

    public List<FittedPurity> all() {
        return all;
    }

    private void fitPurity() throws ExecutionException, InterruptedException {
        final List<Future<List<FittedPurity>>> futures = Lists.newArrayList();
        for (double purity = minPurity; lessOrEqual(purity, maxPurity); purity += purityIncrements) {
            futures.add(executorService.submit(callableFitPurity(purity)));
        }

        for (Future<List<FittedPurity>> future : futures) {
            List<FittedPurity> fittedPurities = future.get();

            if (!fittedPurities.isEmpty()) {
                all.addAll(fittedPurities);
                bestScoringPerPurity.add(fittedPurities.get(0));
            }
        }

        Collections.sort(all);
        Collections.sort(bestScoringPerPurity);
    }

    @NotNull
    private Callable<List<FittedPurity>> callableFitPurity(final double purity) {
        return () -> fitPurity(purity);
    }

    @NotNull
    private List<FittedPurity> fitPurity(final double purity) {
        final List<FittedPurity> fittedPurities = Lists.newArrayList();
        for (double normFactor = minNormFactor; lessOrEqual(normFactor, maxNormFactor); normFactor += normFactorIncrements) {
            double impliedPloidy = PurityAdjuster.impliedSamplePloidy(purity, normFactor);

            if (greaterOrEqual(impliedPloidy, 1) && lessOrEqual(impliedPloidy, maxPloidy)) {
                fittedPurities.add(fitPurity(purity, normFactor));
            }
        }

        Collections.sort(fittedPurities);
        return fittedPurities;
    }

    private double weightWithBaf(double value, int bafCount) {
        return 1d * value * bafCount / totalBAFCount;
    }

    @NotNull
    private FittedPurity fitPurity(final double purity, final double normFactor) {
        ImmutableFittedPurity.Builder builder = ImmutableFittedPurity.builder().purity(purity).normFactor(normFactor);
        double eventPenalty = 0;
        double deviationPenalty = 0;
        double diploidProportion = 0;
        double averagePloidy = 0;

        final List<FittedRegion> fittedRegions = Lists.newArrayList();
        for (final ObservedRegion enrichedRegion : filteredRegions) {
            final FittedRegion fittedRegion = fittedRegionFactory.fitRegion(purity, normFactor, enrichedRegion);
            eventPenalty += weightWithBaf(fittedRegion.eventPenalty(), enrichedRegion.bafCount());
            deviationPenalty += weightWithBaf(fittedRegion.deviationPenalty(), enrichedRegion.bafCount());
            averagePloidy += weightWithBaf(fittedRegion.tumorCopyNumber(), enrichedRegion.bafCount());
            if (fittedRegion.isDiploid()) {
                diploidProportion += weightWithBaf(1, enrichedRegion.bafCount());
            }

            fittedRegions.add(fittedRegion);
        }

        final PurityAdjuster purityAdjuster = new PurityAdjuster(gender, purity, normFactor);
        final double somaticPenalty =
                Doubles.greaterThan(somaticPenaltyWeight, 0) ? SomaticPenaltyFactory.penalty(purityAdjuster, fittedRegions, variants) : 0;

        return builder.score(eventPenalty * deviationPenalty + somaticPenaltyWeight * somaticPenalty)
                .diploidProportion(diploidProportion)
                .ploidy(averagePloidy)
                .somaticPenalty(somaticPenalty)
                .build();
    }
}
