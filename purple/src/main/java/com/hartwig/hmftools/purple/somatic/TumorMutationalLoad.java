package com.hartwig.hmftools.purple.somatic;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.variant.CodingEffect.NONE;
import static com.hartwig.hmftools.common.variant.CodingEffect.UNDEFINED;
import static com.hartwig.hmftools.common.variant.PaveVcfTags.GNOMAD_FREQ;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.KATAEGIS_FLAG;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.KATAEGIS_FLAG_DESCRIPTION;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.UNCLEAR_GERMLINE_FLAG;
import static com.hartwig.hmftools.common.variant.PurpleVcfTags.UNCLEAR_GERMLINE_FLAG_DESCRIPTION;
import static com.hartwig.hmftools.common.variant.VariantType.SNP;
import static com.hartwig.hmftools.common.variant.CommonVcfTags.getGenotypeAttributeAsDouble;
import static com.hartwig.hmftools.purple.PurpleUtils.PPL_LOGGER;
import static com.hartwig.hmftools.purple.config.PurpleConstants.CODING_BASES_PER_GENOME;
import static com.hartwig.hmftools.purple.config.TargetRegionsData.TMB_GENE_EXCLUSIONS;

import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.impact.VariantImpact;
import com.hartwig.hmftools.purple.config.TargetRegionsData;

import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

public class TumorMutationalLoad
{
    private final TargetRegionsData mTargetRegions;
    private double mLoad;
    private double mBurden;
    private int mUnclearVariants;

    public TumorMutationalLoad(final TargetRegionsData targetRegions)
    {
        mTargetRegions = targetRegions;
        mLoad = 0;
        mBurden = 0;
        mUnclearVariants = 0;
    }

    public double load() { return mLoad; }
    public double burden() { return mBurden; }

    public double calcTml()
    {
        if(!mTargetRegions.hasTargetRegions())
            return mLoad;

        double adjustedLoad = mBurden;

        if(mUnclearVariants > 0)
        {
            double unclearFactor = mTargetRegions.codingBases() / mTargetRegions.codingBaseFactor();
            double unclearVariants = pow(mUnclearVariants,2) / (mUnclearVariants + unclearFactor);
            adjustedLoad += unclearVariants;
        }

        double calcTml = adjustedLoad * mTargetRegions.tmlRatio() * CODING_BASES_PER_GENOME / mTargetRegions.codingBases();
        return calcTml;
    }

    public static VCFHeader enrichHeader(final VCFHeader template)
    {
        template.addMetaDataLine(new VCFInfoHeaderLine(UNCLEAR_GERMLINE_FLAG, 0, VCFHeaderLineType.Flag, UNCLEAR_GERMLINE_FLAG_DESCRIPTION));
        return template;
    }

    public void processVariant(final SomaticVariant variant, double purity)
    {
        final VariantImpact variantImpact = variant.variantImpact();

        boolean isUnclearGermline = false;

        if(mTargetRegions.hasTargetRegions())
        {
            if(!mTargetRegions.inTargetRegions(variant.chromosome(), variant.position()))
                return;

            if(variant.isHotspot())
                return;

            if(variant.type() != SNP)
                return;

            if(variantImpact.WorstCodingEffect == NONE || variantImpact.WorstCodingEffect == UNDEFINED)
                return;

            if(TMB_GENE_EXCLUSIONS.contains(variantImpact.CanonicalGeneName))
                return;

            double gnomadFreq = variant.context().getAttributeAsDouble(GNOMAD_FREQ, 0);
            if(gnomadFreq > 0)
                return;

            double rawAf = getGenotypeAttributeAsDouble(variant.context().getGenotype(0), VCFConstants.ALLELE_FREQUENCY_KEY, 0);

            if(rawAf > mTargetRegions.maxAF())
                return;

            // - VCN <= Major Allele CN + min(20%,0.5)
            double variantCn = variant.copyNumber(); // of the segment it's on
            double segmentCn = variant.decorator().adjustedCopyNumber(); // of the segment it's on
            double minorAlleleCn = variant.decorator().minorAlleleCopyNumber();
            double majorAlleleCn = segmentCn - minorAlleleCn;

            /*
            double diffThreshold = min(TARGET_REGIONS_CN_DIFF, majorAlleleCn * TARGET_REGIONS_CN_PERC_DIFF);

            if(variantCn > majorAlleleCn + diffThreshold)
               return;
            */

            double refPurity = 1 - purity;

            double denom = 2 * refPurity + segmentCn * purity;
            double minorVAF = (refPurity + minorAlleleCn * purity) / denom;
            double majorVAF = (refPurity + majorAlleleCn * purity) / denom;

            isUnclearGermline = abs(majorVAF - rawAf) < mTargetRegions.maxAFDiff() || abs(minorVAF - rawAf) < mTargetRegions.maxAFDiff();

            PPL_LOGGER.trace(format("var(%s) af(%.2f) copyNumber(vcn=%.2f segCn=%.2f majorCn=%.2f minorVaf=%.2f majorVaf=%.2f) status(%s) for target-regions TMB",
                    variant.toString(), rawAf, variantCn, segmentCn, majorAlleleCn, minorVAF, majorVAF,
                    isUnclearGermline ? "unclear" : "somatic"));
        }

        if(isUnclearGermline)
        {
            variant.context().getCommonInfo().putAttribute(UNCLEAR_GERMLINE_FLAG, true);
            ++mUnclearVariants;
        }
        else
        {
            ++mBurden;
        }

        if(variantImpact.WorstCodingEffect.equals(CodingEffect.MISSENSE))
            mLoad++;
    }
}
