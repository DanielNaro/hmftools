package com.hartwig.hmftools.common.purple.region;

import com.hartwig.hmftools.common.math.Doubles;

import org.apache.commons.math3.distribution.NormalDistribution;

public class PloidyDeviation {

    private final double standardDeviation;
    private final double minStandardDevitionPerPloidyPloint;
    private final NormalDistribution dist = new NormalDistribution();

    private final double majorAlleleSubOnePenaltyMultiplier;
    private final double majorAlleleSubMinAdditionalPenalty;
    private final double minDeviation;

    PloidyDeviation(final double standardDeviation, final double minStandardDeviationPerPloidyPoint,
            final double majorAlleleSubOnePenaltyMultiplier, final double majorAlleleSubOneAdditionalPenalty, final double minDeviation) {
        this.standardDeviation = standardDeviation;
        this.minStandardDevitionPerPloidyPloint = minStandardDeviationPerPloidyPoint;
        this.majorAlleleSubOnePenaltyMultiplier = Math.abs(majorAlleleSubOnePenaltyMultiplier);
        this.majorAlleleSubMinAdditionalPenalty = Math.abs(majorAlleleSubOneAdditionalPenalty);
        this.minDeviation = minDeviation;
    }

    public double majorAlleleDeviation(final double purity, final double normFactor, final double ploidy) {
        final double majorAlleleDeviationMultiplier =
                Doubles.greaterThan(ploidy, 0) && Doubles.lessThan(ploidy, 1) ? majorAlleleSubOnePenaltyMultiplier : 1;
        final double deviation =
                majorAlleleDeviationMultiplier * alleleDeviation(purity, normFactor, ploidy) + subMinAdditionalPenalty(1, ploidy);
        return Math.max(deviation, minDeviation);
    }

    public double minorAlleleDeviation(final double purity, final double normFactor, final double ploidy) {
        final double deviation = alleleDeviation(purity, normFactor, ploidy) + subMinAdditionalPenalty(0, ploidy);
        return Math.max(deviation, minDeviation);
    }

    private double alleleDeviation(final double purity, final double normFactor, final double ploidy) {
        if (Doubles.lessThan(ploidy, -0.5)) {
            return 1;
        }

        double ploidyDistanceFromInteger = Doubles.absDistanceFromInteger(ploidy);
        double standardDeviationsPerPloidy = Math.max(minStandardDevitionPerPloidyPloint, purity * normFactor / 2 / standardDeviation);
        return 2 * dist.cumulativeProbability(ploidyDistanceFromInteger * standardDeviationsPerPloidy) - 1;
    }

    private double subMinAdditionalPenalty(final double minPloidy, final double ploidy) {
        return Math.min(majorAlleleSubMinAdditionalPenalty, Math.max(0, -majorAlleleSubMinAdditionalPenalty * (ploidy - minPloidy)));
    }
}
