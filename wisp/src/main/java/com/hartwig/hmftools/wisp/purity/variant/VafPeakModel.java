package com.hartwig.hmftools.wisp.purity.variant;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.lang.String.format;

import static com.hartwig.hmftools.wisp.common.CommonUtils.CT_LOGGER;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.SOMATIC_PEAK_MAX_PROBABILITY;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.SOMATIC_PEAK_MIN_DEPTH_PERC;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.SOMATIC_PEAK_MIN_VARIANTS;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.VAF_PEAK_MODEL_MIN_AVG_DEPTH;
import static com.hartwig.hmftools.wisp.purity.variant.ClonalityResult.INVALID_RESULT;

import java.util.Collections;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.common.utils.kde.KernelEstimator;
import com.hartwig.hmftools.wisp.common.SampleData;
import com.hartwig.hmftools.wisp.purity.PurityConfig;
import com.hartwig.hmftools.wisp.purity.ResultsWriter;
import com.hartwig.hmftools.wisp.purity.PurityConstants;

public class VafPeakModel extends ClonalityModel
{
    public VafPeakModel(
            final PurityConfig config, final ResultsWriter resultsWriter, final SampleData sample, final List<SomaticVariant> variants)
    {
        super(config, resultsWriter, sample,  variants);
    }

    @Override
    public ClonalityResult calculate(final String sampleId, final FragmentCalcResult estimatedResult, final double weightedAvgDepth)
    {
        if(estimatedResult.PurityProbability > SOMATIC_PEAK_MAX_PROBABILITY)
            return INVALID_RESULT;

        List<SomaticVariant> filteredVariants = Lists.newArrayList();

        List<Double> variantVafs = Lists.newArrayList();

        int depthThreshold = (int)max(VAF_PEAK_MODEL_MIN_AVG_DEPTH, weightedAvgDepth * SOMATIC_PEAK_MIN_DEPTH_PERC);

        for(SomaticVariant variant : mVariants)
        {
            GenotypeFragments sampleFragData = variant.findGenotypeData(sampleId);

            if(sampleFragData == null)
                continue;

            if(canUseVariant(variant, sampleFragData, depthThreshold))
            {
                filteredVariants.add(variant);
                variantVafs.add(sampleFragData.vaf());
            }
        }

        if(filteredVariants.size() < SOMATIC_PEAK_MIN_VARIANTS)
            return INVALID_RESULT;

        List<VafPeak> vafPeaks = findVafPeaks(variantVafs, estimatedResult.VAF);

        if(!vafPeaks.isEmpty())
        {
            VafPeak maxVafPeak = vafPeaks.get(vafPeaks.size() - 1);
            VafPeak minVafPeak = vafPeaks.get(0);

            CT_LOGGER.debug("sample({}) filteredVars({}) found {} somatic vaf peaks, max({})",
                    sampleId, filteredVariants.size(), vafPeaks.size(), format("%.3f", maxVafPeak.Peak));

            for(VafPeak vafPeak : vafPeaks)
            {
                CT_LOGGER.debug("sample({}) somatic vaf peak({})", sampleId, vafPeak);
            }

            return new ClonalityResult(ClonalityMethod.VAF_PEAK, maxVafPeak.Peak, maxVafPeak.Peak, minVafPeak.Peak, maxVafPeak.Count, 0);
        }

        return INVALID_RESULT;
    }

    private class VafPeak implements Comparable<VafPeak>
    {
        public final double Peak;
        public final int Count;

        public VafPeak(final double peak, final int count)
        {
            Peak = peak;
            Count = count;
        }

        @Override
        public int compareTo(final VafPeak other)
        {
            if(Peak == other.Peak)
                return 0;

            return Peak < other.Peak ? -1 : 1;
        }

        public String toString() { return format("%.3f=%d", Peak, Count); }
    }

    private static final double PEAK_VAF_BUFFER = 0.015;

    private List<VafPeak> findVafPeaks(final List<Double> sampleVafs, double estimatedVaf)
    {
        double vafTotal = 0;
        double maxVaf = 0;
        for(double variantVaf : sampleVafs)
        {
            maxVaf = max(maxVaf, variantVaf);
            vafTotal += variantVaf;
        }

        if(vafTotal == 0)
            return Collections.emptyList();

        double avgVaf = vafTotal / sampleVafs.size();

        double densityBandwidth = max(avgVaf/8, min(avgVaf/2, 0.01));

        int maxVafLimit = min((int)round(maxVaf * 100), 99);

        // VAFs will be allocated to buckets typically of 0.002 increments, so up to 500 altogether, but capped by the max observed VAF
        int vafFraction = 5;
        double[] vafs = IntStream.rangeClosed(0, maxVafLimit * vafFraction).mapToDouble(x -> x / (100d * vafFraction)).toArray();

        KernelEstimator estimator = new KernelEstimator(0.001, densityBandwidth);

        sampleVafs.forEach(x -> estimator.addValue(x, 1.0));

        double[] densities = DoubleStream.of(vafs).map(estimator::getProbability).toArray();

        final List<VafPeak> peakVafs = Lists.newArrayList();

        for(int i = 1; i < densities.length - 1; i++)
        {
            double density = densities[i];

            // peak must be above the estimated VAF
            if(!Doubles.greaterThan(density, densities[i - 1]) || !Doubles.greaterThan(density, densities[i + 1]))
                continue;

            double densityVaf = vafs[i];

            if(densityVaf < estimatedVaf)
                continue;

            // count up observations at this density peak
            int peakCount = 0;

            for(Double variantVar : sampleVafs)
            {
                if(variantVar >= densityVaf - PEAK_VAF_BUFFER && variantVar <= densityVaf + PEAK_VAF_BUFFER)
                    ++peakCount;
            }

            if(peakCount < PurityConstants.SOMATIC_PEAK_MIN_PEAK_VARIANTS)
                continue;

            CT_LOGGER.debug(format("somatic peak: count(%d) vaf(%.3f) densityBandwidth(%.4f)", peakCount, densityVaf, densityBandwidth));
            peakVafs.add(new VafPeak(densityVaf, peakCount));
        }

        Collections.sort(peakVafs);

        return peakVafs;
    }

    private boolean canUseVariant(final SomaticVariant variant, final GenotypeFragments sampleFragData, int depthThreshold)
    {
        return useVariant(variant, sampleFragData)
            && sampleFragData.UmiCounts.totalCount() >= depthThreshold
            && sampleFragData.UmiCounts.alleleCount() >= 1;
    }
}
