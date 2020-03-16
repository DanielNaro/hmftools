package com.hartwig.hmftools.linx.cn;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantData;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CnPloidyCalcs
{
    private final Map<Integer,PloidyCalcData> mSvPloidyCalcMap; // map of sample to SV Id & ploidy calc data

    // references
    private final Map<String,List<SvCNData>> mChrCnDataMap; // map of chromosome to CN data items
    private final Map<Integer,SvCNData[]> mSvIdCnDataMap; // map of SV Ids to corresponding CN data pair
    private final List<StructuralVariantData> mSvDataList;

    private static double ABS_UNCERTAINTY = 0.15;
    private static double RELATIVE_UNCERTAINTY = 0.10;
    private static double ADDITIONAL_ABS_UNCERTAINTY = 0.4;
    private static double ADDITIONAL_REL_UNCERTAINTY = 0.15;
    private static double PROPORTION_CNCHANGE_USED_IN_PLOIDY_UNC = 0.5;

    private static final Logger LOGGER = LogManager.getLogger(CnDataLoader.class);

    public CnPloidyCalcs(
            final Map<String,List<SvCNData>> chrCnDataMap,
            final Map<Integer,SvCNData[]> svIdCnDataMap,
            final List<StructuralVariantData> svDataList)
    {
        mChrCnDataMap = chrCnDataMap;
        mSvIdCnDataMap = svIdCnDataMap;
        mSvDataList = svDataList;

        mSvPloidyCalcMap = Maps.newHashMap();
    }

    public final Map<Integer,PloidyCalcData> getSvPloidyCalcMap() { return mSvPloidyCalcMap; }

    public void calculateAdjustedPloidy(final String sampleId)
    {
        mSvPloidyCalcMap.clear();

        for (Map.Entry<Integer, SvCNData[]> entry : mSvIdCnDataMap.entrySet())
        {
            final int svId = entry.getKey();
            final SvCNData[] cnDataPair = entry.getValue();

            final SvCNData cnStartData = cnDataPair[SE_START];

            if (cnStartData == null || cnStartData.getStructuralVariantData() == null)
            {
                LOGGER.error("SV({}) missing start copy number data or unlined SV data", svId);
                continue;
            }

            StructuralVariantData svData = cnStartData.getStructuralVariantData();

            if(svData.id() != svId)
            {
                svData = getSvDataById(svId);
            }

            /*
            if(svId == SPECIFIC_SV_ID)
            {
                LOGGER.debug("spec SV({})", svId);
            }
            */

            final SvCNData cnStartPrevData = getCNSegment(cnStartData.Chromosome,  cnStartData.getIndex() - 1);

            final SvCNData cnEndNextData = cnDataPair[SE_END];
            final SvCNData cnEndData = cnEndNextData != null ? getCNSegment(cnEndNextData.Chromosome, cnEndNextData.getIndex() - 1) : null;

            int tumorReadCountStart = svData.startTumorVariantFragmentCount();

            double maxCNStart = max(cnStartPrevData.CopyNumber, cnStartData.CopyNumber);
            double maxCNEnd = cnEndData != null && cnEndNextData != null ? max(cnEndData.CopyNumber, cnEndNextData.CopyNumber): 0;

            final int[] startDepthData = { cnStartPrevData.DepthWindowCount, cnStartData.DepthWindowCount };
            double cnChgStart = svData.adjustedStartCopyNumberChange();

            final int[] endDepthData = { cnEndData != null ? cnEndData.DepthWindowCount : 0,
                    cnEndNextData != null ? cnEndNextData.DepthWindowCount : 0 };

            double cnChgEnd = svData.adjustedEndCopyNumberChange();

            // for the special case of a foldback inversion, the CN change isn't reliable for the breakends, but the CN change over the region is reliable
            // so use this value instead
            if(cnEndData != null && svData.type() == INV && (cnStartData.getIndex() == cnEndData.getIndex()))
            {
                if(cnStartPrevData.DepthWindowCount > cnStartData.DepthWindowCount && cnEndNextData.DepthWindowCount > cnStartData.DepthWindowCount)
                {
                    double cnChange = abs(cnStartPrevData.CopyNumber - cnEndNextData.CopyNumber) * 0.5;

                    // use the DWC from the outer segments
                    endDepthData[0] = endDepthData[1];
                    startDepthData[1] = startDepthData[0];
                    cnChgStart = cnChgEnd = cnChange;
                }
            }

            final PloidyCalcData calcResults = calcAdjustedPloidyValues(cnChgStart, cnChgEnd,
                    tumorReadCountStart, svData.ploidy(), maxCNStart, maxCNEnd,
                    startDepthData, cnEndData != null ? endDepthData : null);

            if(!calcResults.Valid)
            {
                LOGGER.debug("sample({}) svID({} type={}) unexpected ploidy(est={} unc={})",
                        sampleId, svData.id(), svData.type(), calcResults.PloidyEstimate, calcResults.PloidyUncertainty);
            }

            mSvPloidyCalcMap.put(svData.id(), calcResults);
        }
    }

    private static double POIS_PROB_LOW = 0.005;
    private static double POIS_PROB_HIGH = 0.995;

    public static PloidyCalcData calcAdjustedPloidyValues(double cnChgStart, double cnChgEnd,
            int tumorReadCount, double ploidy, double maxCNStart, double maxCNEnd, final int[] startDepthData, final int[] endDepthData)
    {
        double cnUncertaintyStart = calcCopyNumberSideUncertainty(maxCNStart, startDepthData);
        double cnUncertaintyEnd = endDepthData != null ? calcCopyNumberSideUncertainty(maxCNEnd, endDepthData) : 0;

        List<Double> observations = Lists.newArrayList();
        List<Double> uncertainties = Lists.newArrayList();

        if(cnUncertaintyStart > 0)
        {
            observations.add(cnChgStart);
            uncertainties.add(cnUncertaintyStart);
        }

        if(cnUncertaintyEnd > 0)
        {
            observations.add(cnChgEnd);
            uncertainties.add(cnUncertaintyEnd);
        }

        if(tumorReadCount > 0)
        {
            double poissonRCLow = calcPoisonReadCount(tumorReadCount, POIS_PROB_LOW);
            double poissonRCHigh = calcPoisonReadCount(tumorReadCount, POIS_PROB_HIGH);

            double rcAdjustedPloidy = ploidy * ((poissonRCLow + poissonRCHigh) * 0.5) / tumorReadCount;

            // Ploidy Uncertainty = Ploidy * (ReadCountUpperCI-ReadCountLowerCI)* 0.5 / ObservedReadCount + 0.5 * min(CNChangeUncertaintyStart,CNChangeUncertaintyEnd)

            double ploidyUncertainty = rcAdjustedPloidy * (poissonRCHigh - poissonRCLow) / tumorReadCount * 0.5;

            double cnUncertaintyFactor = !uncertainties.isEmpty() ? uncertainties.stream().mapToDouble(x -> x).sum() / uncertainties.size() : 0;

            ploidyUncertainty += cnUncertaintyFactor * PROPORTION_CNCHANGE_USED_IN_PLOIDY_UNC;

            if (ploidyUncertainty > 0)
            {
                observations.add(rcAdjustedPloidy);
                uncertainties.add(ploidyUncertainty);
            }
        }

        double estPloidy = 0;
        double estUncertainty = 0;

        if(observations.size() == 1)
        {
            estPloidy = observations.get(0);
            estUncertainty = uncertainties.get(0);
        }
        else
        {
            // A = countObservations/(countObservations-1)
            // B = SUM[(1/Uncertainty(i)^2*(MAX(Observation(i)-consolidatedPloidy,Uncertainty(i)/2))^2]
            // K = SUM(1/Uncertainty(i)^2)
            // net uncertainty = SQRT(A * B / K)

            double sumUncertainty = 0;
            double sumObservedUncertainty = 0;

            for(int i = 0; i < observations.size(); ++i)
            {
                double uncertInvSqrd = 1 / pow(uncertainties.get(i), 2);
                sumUncertainty += uncertInvSqrd;
                sumObservedUncertainty += observations.get(i) * uncertInvSqrd;
            }

            // consolidatedPloidy =  SUM[Observation(i)*(1/Uncertainty(i)^2)] / Sum[1/Uncertainty(i)^2]
            estPloidy = sumObservedUncertainty / sumUncertainty;

            double adjUncertainty = 0;

            for(int i = 0; i < observations.size(); ++i)
            {
                double uncertInvSqrd = 1 / pow(uncertainties.get(i), 2);
                double relativeUncertainty = uncertInvSqrd * pow(max(observations.get(i) - estPloidy, uncertainties.get(i)/2),2);
                adjUncertainty += relativeUncertainty;
            }

            estUncertainty = sqrt(observations.size() / (double)(observations.size() - 1) * adjUncertainty / sumUncertainty);
        }

        if(Double.isNaN(estPloidy) || estPloidy <= 0 || Double.isNaN(estUncertainty) || estUncertainty <= 0)
            return new PloidyCalcData(0,0, false);

        return new PloidyCalcData(estPloidy, estUncertainty, true);
    }

    private static double calcCopyNumberSideUncertainty(double copyNumber, final int[] depthData)
    {
        double minDepthCount = max(min(depthData[0], depthData[1]), 0.1);

        // MAX(copyNumber*relUncertainty,absUncertainty)+MAX(addAbsUncertainty,addRelUncertainty*copyNumber)/SQRT(MAX(minDepthCount,1))
        double uncertainty = max(copyNumber*RELATIVE_UNCERTAINTY, ABS_UNCERTAINTY);
        uncertainty += max(ADDITIONAL_ABS_UNCERTAINTY, ADDITIONAL_REL_UNCERTAINTY * copyNumber) / sqrt(minDepthCount);

        return uncertainty;
    }

    private static double calcPoisonReadCount(int readCount, double requiredProb)
    {
        return calcPoissonObservedGivenProb(readCount, requiredProb);
    }

    private static int calcPoissonObservedGivenProb(int expectedVal, double requiredProb)
    {
        if(expectedVal <= 0)
            return 0;

        PoissonDistribution poisson = new PoissonDistribution(expectedVal);

        int maxIterations = 20;
        int iterations = 0;

        double refCount = 25;
        double refRangePerc = 0.44;
        double rangePerc = refRangePerc / sqrt(expectedVal/refCount);
        double range = rangePerc * expectedVal;

        int testValueUpper;
        int testValueLower;

        if(requiredProb > 0.5)
        {
            testValueUpper = (int) round(expectedVal + range * 1.5);
            testValueLower = (int) round(max(expectedVal + range * 0.2, 0));
        }
        else
        {
            testValueUpper = (int) round(expectedVal - range * 0.2);
            testValueLower = (int) round(max(expectedVal - range * 1.2, 0));
        }

        int testValue = (int)((testValueLower + testValueUpper) * 0.5);

        double currentProb = poisson.cumulativeProbability(testValue);
        double probDiff = 0;

        while(iterations < maxIterations)
        {
            probDiff = abs(requiredProb - currentProb) / requiredProb;

            if(probDiff < 0.001)
                break;

            // if prob is too high, need to lower the test value
            if(currentProb > requiredProb)
            {
                if(testValue <= testValueLower + 1)
                    break;

                testValueUpper = testValue;
                testValue = (int)round((testValue + testValueLower) * 0.5);
            }
            else
            {
                if(testValue >= testValueUpper - 1)
                    break;

                testValueLower = testValue;
                testValue = (int)round((testValue + testValueUpper) * 0.5);
            }

            currentProb = poisson.cumulativeProbability(testValue);
            ++iterations;
        }

        if(iterations >= maxIterations)
        {
            LOGGER.warn(String.format("max iterations reached: value(%d) test(%d) prob(%.4f diff=%.4f)",
                    expectedVal, testValue, currentProb, probDiff));
        }

        return testValue;
    }

    public final SvCNData getCNSegment(final String chromosome, int index)
    {
        final List<SvCNData> cnDataList = mChrCnDataMap.get(chromosome);

        if(cnDataList == null || cnDataList.isEmpty())
            return null;

        if(index < 0 || index >= cnDataList.size())
            return null;

        return cnDataList.get(index);
    }

    private StructuralVariantData getSvDataById(int svId)
    {
        for (final StructuralVariantData svData : mSvDataList)
        {
            if(svData.id() == svId)
                return svData;
        }

        return null;
    }


}
