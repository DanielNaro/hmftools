package com.hartwig.hmftools.orange.conversion;

import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.genome.chromosome.GermlineAberration;
import com.hartwig.hmftools.common.purple.GeneCopyNumber;
import com.hartwig.hmftools.common.purple.GermlineDeletion;
import com.hartwig.hmftools.common.variant.AllelicDepth;
import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.impact.VariantEffect;
import com.hartwig.hmftools.common.variant.impact.VariantTranscriptImpact;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleAllelicDepth;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleCopyNumber;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleDriver;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleGeneCopyNumber;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleGermlineDeletion;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleQC;
import com.hartwig.hmftools.datamodel.purple.ImmutablePurpleTranscriptImpact;
import com.hartwig.hmftools.datamodel.purple.PurpleAllelicDepth;
import com.hartwig.hmftools.datamodel.purple.PurpleCodingEffect;
import com.hartwig.hmftools.datamodel.purple.PurpleCopyNumber;
import com.hartwig.hmftools.datamodel.purple.PurpleDriver;
import com.hartwig.hmftools.datamodel.purple.PurpleDriverType;
import com.hartwig.hmftools.datamodel.purple.PurpleGeneCopyNumber;
import com.hartwig.hmftools.datamodel.purple.PurpleGermlineAberration;
import com.hartwig.hmftools.datamodel.purple.PurpleGermlineDeletion;
import com.hartwig.hmftools.datamodel.purple.PurpleGermlineDetectionMethod;
import com.hartwig.hmftools.datamodel.purple.PurpleGermlineStatus;
import com.hartwig.hmftools.datamodel.purple.PurpleLikelihoodMethod;
import com.hartwig.hmftools.datamodel.purple.PurpleQC;
import com.hartwig.hmftools.datamodel.purple.PurpleQCStatus;
import com.hartwig.hmftools.datamodel.purple.PurpleTranscriptImpact;
import com.hartwig.hmftools.datamodel.purple.PurpleVariantEffect;
import com.hartwig.hmftools.orange.algo.purple.CodingEffectDeterminer;

import org.jetbrains.annotations.NotNull;

public final class PurpleConversion
{

    private PurpleConversion()
    {
    }

    @NotNull
    public static PurpleCopyNumber convert(com.hartwig.hmftools.common.purple.PurpleCopyNumber copyNumber)
    {
        return ImmutablePurpleCopyNumber.builder()
                .chromosome(copyNumber.chromosome())
                .start(copyNumber.start())
                .end(copyNumber.end())
                .averageTumorCopyNumber(copyNumber.averageTumorCopyNumber())
                .build();
    }

    @NotNull
    public static PurpleGeneCopyNumber convert(GeneCopyNumber geneCopyNumber)
    {
        return ImmutablePurpleGeneCopyNumber.builder()
                .chromosome(geneCopyNumber.chromosome())
                .chromosomeBand(geneCopyNumber.chromosomeBand())
                .geneName(geneCopyNumber.geneName())
                .minCopyNumber(geneCopyNumber.minCopyNumber())
                .minMinorAlleleCopyNumber(geneCopyNumber.minMinorAlleleCopyNumber())
                .build();
    }

    @NotNull
    public static PurpleDriver convert(DriverCatalog catalog)
    {
        return ImmutablePurpleDriver.builder()
                .gene(catalog.gene())
                .transcript(catalog.transcript())
                .driver(PurpleDriverType.valueOf(catalog.driver().name()))
                .driverLikelihood(catalog.driverLikelihood())
                .likelihoodMethod(PurpleLikelihoodMethod.valueOf(catalog.likelihoodMethod().name()))
                .isCanonical(catalog.isCanonical())
                .build();
    }

    @NotNull
    public static PurpleQC convert(@NotNull com.hartwig.hmftools.common.purple.PurpleQC purpleQC)
    {
        return ImmutablePurpleQC.builder()
                .status(ConversionUtil.mapToIterable(purpleQC.status(), PurpleConversion::convert))
                .germlineAberrations(ConversionUtil.mapToIterable(purpleQC.germlineAberrations(), PurpleConversion::convert))
                .amberMeanDepth(purpleQC.amberMeanDepth())
                .contamination(purpleQC.contamination())
                .unsupportedCopyNumberSegments(purpleQC.unsupportedCopyNumberSegments())
                .deletedGenes(purpleQC.deletedGenes())
                .build();
    }

    @NotNull
    public static PurpleAllelicDepth convert(AllelicDepth allelicDepth)
    {
        return ImmutablePurpleAllelicDepth.builder()
                .alleleReadCount(allelicDepth.alleleReadCount())
                .totalReadCount(allelicDepth.totalReadCount())
                .build();
    }

    @NotNull
    public static PurpleGermlineDeletion convert(GermlineDeletion germlineDeletion)
    {
        return ImmutablePurpleGermlineDeletion.builder()
                .geneName(germlineDeletion.GeneName)
                .chromosome(germlineDeletion.Chromosome)
                .chromosomeBand(germlineDeletion.ChromosomeBand)
                .regionStart(germlineDeletion.RegionStart)
                .regionEnd(germlineDeletion.RegionEnd)
                .depthWindowCount(germlineDeletion.DepthWindowCount)
                .exonStart(germlineDeletion.ExonStart)
                .exonEnd(germlineDeletion.ExonEnd)
                .detectionMethod(PurpleGermlineDetectionMethod.valueOf(germlineDeletion.DetectionMethod.name()))
                .normalStatus(PurpleGermlineStatus.valueOf(germlineDeletion.NormalStatus.name()))
                .tumorStatus(PurpleGermlineStatus.valueOf(germlineDeletion.TumorStatus.name()))
                .germlineCopyNumber(germlineDeletion.GermlineCopyNumber)
                .tumorCopyNumber(germlineDeletion.TumorCopyNumber)
                .filter(germlineDeletion.Filter)
                .cohortFrequency(germlineDeletion.CohortFrequency)
                .reported(germlineDeletion.Reported)
                .build();
    }

    @NotNull
    public static PurpleGermlineAberration convert(GermlineAberration aberration)
    {
        return PurpleGermlineAberration.valueOf(aberration.name());
    }

    @NotNull
    public static PurpleQCStatus convert(com.hartwig.hmftools.common.purple.PurpleQCStatus qcStatus)
    {
        return PurpleQCStatus.valueOf(qcStatus.name());
    }

    public static PurpleCodingEffect convert(CodingEffect effect)
    {
        return PurpleCodingEffect.valueOf(effect.name());
    }

    @NotNull
    public static PurpleVariantEffect convert(VariantEffect effect)
    {
        return PurpleVariantEffect.valueOf(effect.name());
    }

    @NotNull
    public static PurpleTranscriptImpact convert(VariantTranscriptImpact impact)
    {
        var effectsList = VariantEffect.effectsToList(impact.Effects);
        var purpleEffects = ConversionUtil.mapToList(effectsList, PurpleConversion::convert);
        var purpleCodingEffect = convert(CodingEffectDeterminer.determineCodingEffect(effectsList));

        /*  When VariantTranscriptImpacts are created from the VCF file, it sometimes (incorrectly) parses the square array brackets.
            These parsed brackets are then included in the impacts fields.
            Here, we make no assumption what the field order is in the underlying VCF file, and we just check if the square bracket is
            included, if so we remove them. */
        // TODO maybe fix this bug upstream?
        return ImmutablePurpleTranscriptImpact.builder()
                .transcript(stripSquareBrackets(impact.Transcript))
                .hgvsCodingImpact(stripSquareBrackets(impact.HgvsCoding))
                .hgvsProteinImpact(stripSquareBrackets(impact.HgvsProtein))
                .effects(purpleEffects)
                .codingEffect(purpleCodingEffect)
                .spliceRegion(impact.SpliceRegion)
                .build();
    }

    private static String stripSquareBrackets(String s)
    {
        s = s.startsWith("[") ? s.substring(1) : s;
        return s.endsWith("]") ? s.substring(0, s.length() - 1) : s;
    }

}
