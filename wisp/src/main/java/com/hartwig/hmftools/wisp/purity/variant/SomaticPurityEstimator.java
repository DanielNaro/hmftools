package com.hartwig.hmftools.wisp.purity.variant;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.stats.PoissonCalcs.calcPoissonNoiseValue;
import static com.hartwig.hmftools.wisp.common.CommonUtils.CT_LOGGER;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.HIGH_PROBABILITY;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.LOW_PROBABILITY;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.SNV_QUAL_THRESHOLDS;
import static com.hartwig.hmftools.wisp.purity.PurityConstants.SYNTHETIC_TUMOR_VAF;
import static com.hartwig.hmftools.wisp.purity.variant.BqrAdjustment.hasVariantContext;
import static com.hartwig.hmftools.wisp.purity.variant.ClonalityMethod.isRecomputed;
import static com.hartwig.hmftools.wisp.purity.variant.LowCountModel.filterVariants;
import static com.hartwig.hmftools.wisp.purity.variant.PurityCalcData.CALC_NO_SET;
import static com.hartwig.hmftools.wisp.purity.variant.SomaticPurityCalcs.calcLimitOfDetection;
import static com.hartwig.hmftools.wisp.purity.variant.SomaticPurityCalcs.estimatedProbability;
import static com.hartwig.hmftools.wisp.purity.variant.SomaticPurityCalcs.estimatedProbabilityOld;
import static com.hartwig.hmftools.wisp.purity.variant.SomaticPurityCalcs.estimatedPurity;
import static com.hartwig.hmftools.wisp.purity.variant.SomaticPurityResult.INVALID_RESULT;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.hartwig.hmftools.common.purple.PurityContext;
import com.hartwig.hmftools.wisp.purity.SampleData;
import com.hartwig.hmftools.wisp.purity.PurityConfig;
import com.hartwig.hmftools.wisp.purity.ResultsWriter;

public class SomaticPurityEstimator
{
    private final PurityConfig mConfig;
    private final ResultsWriter mResultsWriter;
    private final SampleData mSample;
    private final BqrAdjustment mBqrAdjustment;

    public SomaticPurityEstimator(final PurityConfig config, final ResultsWriter resultsWriter, final SampleData sample)
    {
        mConfig = config;
        mResultsWriter = resultsWriter;
        mSample = sample;
        mBqrAdjustment = new BqrAdjustment(mConfig);
    }

    public SomaticPurityResult calculatePurity(
            final String sampleId, final PurityContext purityContext, final List<SomaticVariant> variants,
            final int totalVariantCount, final int chipVariants)
    {
        FragmentTotals fragmentTotals = new FragmentTotals();

        int sampleDualDP = 0;
        int sampleDualAD = 0;

        UmiTypeCounts umiTypeCounts = new UmiTypeCounts();

        for(SomaticVariant variant : variants)
        {
            GenotypeFragments sampleFragData = variant.findGenotypeData(sampleId);
            GenotypeFragments tumorFragData = variant.findGenotypeData(mSample.TumorId);

            fragmentTotals.addVariantData(
                    variant.copyNumber(), tumorFragData.AlleleCount, sampleFragData.AlleleCount,
                    tumorFragData.Depth, sampleFragData.Depth, sampleFragData.QualTotal);

            umiTypeCounts.add(sampleFragData.UmiCounts);
            sampleDualDP += sampleFragData.UmiCounts.TotalDual;
            sampleDualAD += sampleFragData.UmiCounts.AlleleDual;
        }

        if(fragmentTotals.sampleDepthTotal() == 0)
            return INVALID_RESULT;

        if(!mConfig.SkipBqr)
            mBqrAdjustment.loadBqrData(sampleId);

        double tumorPurity = purityContext.bestFit().purity();

        if(mConfig.hasSyntheticTumor())
        {
            fragmentTotals.setTumorVafOverride(SYNTHETIC_TUMOR_VAF);
        }

        PurityCalcData purityCalcData = new PurityCalcData();

        // firstly estimate raw purity without consideration of clonal peaks
        double noiseRate = mConfig.noiseRate(false);

        purityCalcData.RawPurityEstimate = estimatedPurity(
                tumorPurity, fragmentTotals.adjTumorVaf(), fragmentTotals.adjSampleVaf(), noiseRate);

        purityCalcData.PurityEstimate = purityCalcData.RawPurityEstimate;

        ClonalityModel model = null;

        if(VafPeakModel.canUseModel(fragmentTotals))
        {
            model = new VafPeakModel(mConfig, mResultsWriter, mSample, variants);
        }
        else
        {
            double medianVcn = medianVcn(variants);

            List<SomaticVariant> lowCountFilteredVariants = filterVariants(sampleId, fragmentTotals, variants, medianVcn);

            if(LowCountModel.canUseModel(sampleId, fragmentTotals, lowCountFilteredVariants))
                model = new LowCountModel(mConfig, mResultsWriter, mSample, lowCountFilteredVariants);
        }

        if(model != null)
        {
            purityCalcData.Clonality = model.calculate(sampleId, fragmentTotals, purityCalcData.RawPurityEstimate);

            if(isRecomputed(purityCalcData.Clonality.Method))
            {
                purityCalcData.PurityEstimate = estimatedPurity(
                        tumorPurity, fragmentTotals.adjTumorVaf(), purityCalcData.Clonality.Vaf, noiseRate);

                purityCalcData.PurityRangeLow = estimatedPurity(
                        tumorPurity, fragmentTotals.adjTumorVaf(), purityCalcData.Clonality.VafLow, noiseRate);

                purityCalcData.PurityRangeHigh = estimatedPurity(
                        tumorPurity, fragmentTotals.adjTumorVaf(), purityCalcData.Clonality.VafHigh, noiseRate);
            }
        }
        else
        {
            int alleleCount = fragmentTotals.sampleAdTotal();

            double lowProbAlleleCount = calcPoissonNoiseValue(alleleCount, HIGH_PROBABILITY);
            double sampleAdjVafLow = fragmentTotals.adjSampleVaf(lowProbAlleleCount - alleleCount);
            purityCalcData.PurityRangeLow = estimatedPurity(tumorPurity, fragmentTotals.adjTumorVaf(), sampleAdjVafLow, noiseRate);

            double highProbAlleleCount = calcPoissonNoiseValue(alleleCount, LOW_PROBABILITY);
            double sampleAdjVafHigh = fragmentTotals.adjSampleVaf(highProbAlleleCount - alleleCount);
            purityCalcData.PurityRangeHigh = estimatedPurity(tumorPurity, fragmentTotals.adjTumorVaf(), sampleAdjVafHigh, noiseRate);
        }

        // calculate a limit-of-detection (LOD), being the number of fragments that would return a 99% confidence of a tumor presence
        if(!mConfig.SkipBqr)
        {
            for(double threshold : SNV_QUAL_THRESHOLDS)
            {
                calculateThresholdValues(sampleId, purityCalcData, variants, threshold);
            }
        }
        else
        {
            purityCalcData.Probability = estimatedProbability(fragmentTotals, noiseRate);
            purityCalcData.LodPurityEstimate = calcLimitOfDetection(fragmentTotals, noiseRate);
        }

        // double expectedNoiseFragments = noiseRate * fragmentTotals.sampleDepthTotal();
        // double probabilityOld = estimatedProbabilityOld(fragmentTotals.sampleAdTotal(), expectedNoiseFragments);
        // purityCalcData.Probability = estimatedProbability(fragmentTotals, noiseRate);

        // purityCalcData.LodPurityEstimate = calcLimitOfDetection(fragmentTotals, noiseRate);

        // report final probability as min of Dual and Normal Prob
        double expectedDualNoiseFragments = mConfig.noiseRate(true) * sampleDualDP;
        purityCalcData.DualProbability = estimatedProbabilityOld(sampleDualAD, expectedDualNoiseFragments);

        // CT_LOGGER.info(format("patient(%s) sample(%s) sampleTotalFrags(%d) noise(%.1f) LOD(%.6f)",
        //        mSample.PatientId, sampleId, sampleDepthTotal, allFragsNoise, lodFragsResult.EstimatedPurity));

        return new SomaticPurityResult(true, totalVariantCount, chipVariants, fragmentTotals, umiTypeCounts, purityCalcData);
    }

    private void calculateThresholdValues(
            final String sampleId, final PurityCalcData purityCalcData, final List<SomaticVariant> variants, double qualThreshold)
    {
        List<BqrContextData> filteredBqrData = mBqrAdjustment.getThresholdBqrData(qualThreshold);

        if(filteredBqrData.isEmpty())
            return;

        double bqrErrorRate = BqrAdjustment.calcErrorRate(filteredBqrData);

        FragmentTotals fragmentTotals = new FragmentTotals();

        for(SomaticVariant variant : variants)
        {
            if(!hasVariantContext(filteredBqrData, variant.decorator().trinucleotideContext(), variant.Alt))
                continue;

            GenotypeFragments sampleFragData = variant.findGenotypeData(sampleId);
            GenotypeFragments tumorFragData = variant.findGenotypeData(mSample.TumorId);

            fragmentTotals.addVariantData(
                    variant.copyNumber(), tumorFragData.AlleleCount, sampleFragData.AlleleCount,
                    tumorFragData.Depth, sampleFragData.Depth, sampleFragData.QualTotal);
        }

        if(fragmentTotals.sampleDepthTotal() == 0)
            return;

        double probability = estimatedProbability(fragmentTotals, bqrErrorRate);

        double lodPurityEstimate = calcLimitOfDetection(fragmentTotals, bqrErrorRate);

        CT_LOGGER.debug(format("patient(%s) sample(%s) errorRatePM(%.1f) variants(%d) frags(dp=%d ad=%d)  probability(%.6f) LOD(%.6f)",
                mSample.PatientId, sampleId, bqrErrorRate * 1_000_000, fragmentTotals.variantCount(),
                fragmentTotals.sampleDepthTotal(), fragmentTotals.sampleAdTotal(), probability, lodPurityEstimate));

        if(purityCalcData.Probability == CALC_NO_SET || probability < purityCalcData.Probability)
            purityCalcData.Probability = lodPurityEstimate;

        if(purityCalcData.LodPurityEstimate == CALC_NO_SET || lodPurityEstimate < purityCalcData.LodPurityEstimate)
            purityCalcData.LodPurityEstimate = lodPurityEstimate;
    }

    private static double medianVcn(final List<SomaticVariant> variants)
    {
        List<Double> variantCopyNumbers = variants.stream().map(x -> x.variantCopyNumber()).collect(Collectors.toList());
        Collections.sort(variantCopyNumbers);

        int medIndex = variantCopyNumbers.size() / 2;
        return variantCopyNumbers.get(medIndex);
    }
}
