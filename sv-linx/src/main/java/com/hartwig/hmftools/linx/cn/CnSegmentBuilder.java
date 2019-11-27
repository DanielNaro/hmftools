package com.hartwig.hmftools.linx.cn;

import static java.lang.Math.max;

import static com.hartwig.hmftools.common.purple.gender.Gender.MALE;
import static com.hartwig.hmftools.common.purple.purity.FittedPurityStatus.NORMAL;
import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.CENTROMERE;
import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.TELOMERE;
import static com.hartwig.hmftools.common.variant.msi.MicrosatelliteStatus.UNKNOWN;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.CHROMOSOME_ARM_P;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.CHROMOSOME_ARM_Q;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.purity.FittedPurity;
import com.hartwig.hmftools.common.purple.purity.FittedPurityScore;
import com.hartwig.hmftools.common.purple.purity.ImmutableFittedPurity;
import com.hartwig.hmftools.common.purple.purity.ImmutableFittedPurityScore;
import com.hartwig.hmftools.common.purple.purity.ImmutablePurityContext;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantData;
import com.hartwig.hmftools.linx.analysis.SvUtilities;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CnSegmentBuilder
{
    // assume an A-allele which is unaffected by the SVs, and a B-allele which is
    private double mOtherAllelePloidy;
    private double mUndisruptedAllelePloidy; // the ploidy of the undisrupted B-allele


    private static final Logger LOGGER = LogManager.getLogger(CnSegmentBuilder.class);

    public CnSegmentBuilder()
    {
        mOtherAllelePloidy = 1;
        mUndisruptedAllelePloidy = 0;
    }

    public void setAllelePloidies(double otherAllele, double undisruptedAllele)
    {
        mOtherAllelePloidy = otherAllele;
        mUndisruptedAllelePloidy = undisruptedAllele;
    }

    public void createCopyNumberData(final CnDataLoader cnDataLoader, final Map<String, List<SvBreakend>> chrBreakendMap)
    {
        // use SV breakend data to re-create the copy number segments

        Map<String, List<SvCNData>> chrCnDataMap = cnDataLoader.getChrCnDataMap();
        Map<Integer,SvCNData[]> svIdCnDataMap = cnDataLoader.getSvIdCnDataMap();

        chrCnDataMap.clear();
        svIdCnDataMap.clear();

        int cnId = 0;
        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            final String chromosome = entry.getKey();
            List<SvBreakend> breakendList = entry.getValue();
            List<SvCNData> cnDataList = Lists.newArrayList();
            chrCnDataMap.put(chromosome, cnDataList);

            // work out the net copy number from all SVs going out to P-arm telomere for the correct starting copy number
            double netSvPloidy = max(breakendList.stream().mapToDouble(x -> x.ploidy() * x.orientation()).sum(), 0);

            double currentCopyNumber = mOtherAllelePloidy + mUndisruptedAllelePloidy + netSvPloidy;

            long centromerePosition = SvUtilities.getChromosomalArmLength(chromosome, CHROMOSOME_ARM_P);
            long chromosomeLength = SvUtilities.getChromosomeLength(chromosome);

            for (int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                final StructuralVariantData svData = breakend.getSV().getSvData();
                final SvVarData var = breakend.getSV();
                double ploidy = var.ploidy();

                double ploidyChange = -ploidy * breakend.orientation();

                SvCNData cnData = null;

                if (i == 0)
                {
                    if(breakend.getSV().type() == DUP && breakendList.get(i + 1).getSV() == breakend.getSV())
                    {
                        // starts with a DUP so don't treat the first breakend as a copy-number drop
                        currentCopyNumber += ploidyChange;
                    }
                    else
                    {
                        currentCopyNumber += max(-ploidyChange, 0);
                    }

                    if(currentCopyNumber < 0)
                    {
                        LOGGER.error("invalid copy number({}) at telomere", currentCopyNumber);
                        return;
                    }

                    double actualBaf = calcActualBaf(currentCopyNumber);

                    // add telomere segment at start, and centromere as soon as the breakend crosses the centromere
                    if(breakend.arm() == CHROMOSOME_ARM_Q)
                    {
                        SvCNData extraCnData = new SvCNData(cnId++, chromosome, 0, centromerePosition,
                                currentCopyNumber, TELOMERE.toString(), CENTROMERE.toString(),
                                1, actualBaf, 100);

                        extraCnData.setIndex(cnDataList.size());
                        cnDataList.add(extraCnData);

                        extraCnData = new SvCNData(cnId++, chromosome, centromerePosition, breakend.position() - 1,
                                currentCopyNumber, CENTROMERE.toString(), var.type().toString(),
                                1, actualBaf, 100);

                        extraCnData.setIndex(cnDataList.size());
                        cnDataList.add(extraCnData);
                    }
                    else
                    {
                        SvCNData extraCnData = new SvCNData(cnId++, chromosome, 0, breakend.position() - 1,
                                currentCopyNumber, TELOMERE.toString(), var.type().toString(),
                                1, actualBaf, 100);

                        extraCnData.setIndex(cnDataList.size());
                        cnDataList.add(extraCnData);
                    }
                }

                // orientation determines copy number drop or gain
                currentCopyNumber += ploidyChange;

                if(currentCopyNumber < 0)
                {
                    LOGGER.error("invalid copy number({}) at breakend({})", currentCopyNumber, breakend);
                    return;
                }

                double actualBaf = calcActualBaf(currentCopyNumber);

                if (i < breakendList.size() - 1)
                {
                    final SvBreakend nextBreakend = breakendList.get(i + 1);

                    if(breakend.arm() == CHROMOSOME_ARM_P && nextBreakend.arm() == CHROMOSOME_ARM_Q)
                    {
                        cnData = new SvCNData(cnId++, chromosome, breakend.position(), centromerePosition-1,
                                currentCopyNumber, var.type().toString(), CENTROMERE.toString(),
                                1, actualBaf, 100);

                        cnData.setIndex(cnDataList.size());
                        cnData.setStructuralVariantData(svData, breakend.usesStart());
                        cnDataList.add(cnData);

                        SvCNData extraCnData = new SvCNData(cnId++, chromosome, centromerePosition, nextBreakend.position() - 1,
                                currentCopyNumber, CENTROMERE.toString(), nextBreakend.getSV().type().toString(),
                                1, actualBaf, 100);

                        extraCnData.setIndex(cnDataList.size());
                        cnDataList.add(extraCnData);
                    }
                    else
                    {
                        cnData = new SvCNData(cnId++, chromosome, breakend.position(), nextBreakend.position() - 1,
                                currentCopyNumber, var.type().toString(), nextBreakend.getSV().type().toString(),
                                1, actualBaf, 100);

                        cnData.setIndex(cnDataList.size());
                        cnData.setStructuralVariantData(svData, breakend.usesStart());
                        cnDataList.add(cnData);
                    }
                }
                else
                {
                    // last breakend runs out to the telomere
                    if(breakend.arm() == CHROMOSOME_ARM_P)
                    {
                        cnData = new SvCNData(cnId++, chromosome, breakend.position(), centromerePosition - 1,
                                currentCopyNumber,
                                var.type().toString(), CENTROMERE.toString(),
                                1, actualBaf, 100);

                        cnData.setIndex(cnDataList.size());
                        cnData.setStructuralVariantData(svData, breakend.usesStart());
                        cnDataList.add(cnData);

                        SvCNData extraCnData = new SvCNData(cnId++, chromosome, centromerePosition, chromosomeLength,
                                currentCopyNumber,
                                CENTROMERE.toString(), TELOMERE.toString(),
                                1, 0.5, 100);

                        extraCnData.setIndex(cnDataList.size());
                        cnDataList.add(extraCnData);
                    }
                    else
                    {
                        cnData = new SvCNData(cnId++, chromosome, breakend.position(), chromosomeLength,
                                currentCopyNumber, var.type().toString(), TELOMERE.toString(),
                                1, actualBaf, 100);

                        cnData.setIndex(cnDataList.size());
                        cnData.setStructuralVariantData(svData, breakend.usesStart());
                        cnDataList.add(cnData);
                    }
                }

                SvCNData[] cnDataPair = svIdCnDataMap.get(var.id());

                if(cnDataPair == null)
                {
                    cnDataPair = new SvCNData[2];
                    svIdCnDataMap.put(var.id(), cnDataPair);
                }

                cnDataPair[breakend.usesStart() ? SE_START : SE_END] = cnData;

                // set copy number data back into the SV
                double beCopyNumber = breakend.orientation() == 1 ? currentCopyNumber + ploidy : currentCopyNumber;
                breakend.getSV().setCopyNumberData(breakend.usesStart(), beCopyNumber, ploidy);
            }
        }
    }

    private double calcActualBaf(double copyNumber)
    {
        if(copyNumber == 0)
            return 0;

        double bAllelePloidy = max(copyNumber - mOtherAllelePloidy, 0);

        if(bAllelePloidy >= mOtherAllelePloidy)
            return bAllelePloidy / copyNumber;
        else
            return mOtherAllelePloidy / copyNumber;
    }

    public void setSamplePurity(final CnDataLoader cnDataLoader, double purity, double ploidy, Gender gender)
    {
        FittedPurity fittedPurity = ImmutableFittedPurity.builder()
                .purity(purity)
                .ploidy(ploidy)
                .diploidProportion(1)
                .normFactor(1)
                .score(1)
                .somaticPenalty(0)
                .build();

        FittedPurityScore purityScore = ImmutableFittedPurityScore.builder()
                .maxPurity(1)
                .minPurity(1)
                .maxDiploidProportion(1)
                .maxPloidy(2)
                .minDiploidProportion(0)
                .minPloidy(2)
                .build();

        PurityContext purityContext = ImmutablePurityContext.builder()
                .bestFit(fittedPurity)
                .gender(gender)
                .microsatelliteIndelsPerMb(0)
                .microsatelliteStatus(UNKNOWN)
                .polyClonalProportion(0)
                .score(purityScore)
                .status(NORMAL)
                .version("1.0")
                .wholeGenomeDuplication(false)
                .build();

        cnDataLoader.setPurityContext(purityContext);
    }


}
