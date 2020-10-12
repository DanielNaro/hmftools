package com.hartwig.hmftools.sage.variant;

import static com.hartwig.hmftools.common.sage.SageMetaData.LOCAL_PHASE_SET;
import static com.hartwig.hmftools.common.sage.SageMetaData.LOCAL_REALIGN_SET;
import static com.hartwig.hmftools.common.sage.SageMetaData.PHASED_INFRAME_INDEL;
import static com.hartwig.hmftools.sage.vcf.SageVCF.MIXED_SOMATIC_GERMLINE;
import static com.hartwig.hmftools.sage.vcf.SageVCF.PASS;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RAW_ALLELIC_BASE_QUALITY;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RAW_ALLELIC_DEPTH;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RAW_DEPTH;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_COUNT;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_IMPROPER_PAIR;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_JITTER;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_QUALITY;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RIGHT_ALIGNED_MICROHOMOLOGY;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.candidate.CandidateSerialization;
import com.hartwig.hmftools.sage.read.ReadContextCounter;

import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFConstants;

public class SageVariantContextFactory {

    private static final double HET_CUTOFF = 0.1;

    @NotNull
    public static VariantContext addGenotype(@NotNull final VariantContext parent, @NotNull final List<ReadContextCounter> counters) {
        final VariantContextBuilder builder = new VariantContextBuilder(parent);
        final List<Genotype> genotypes = Lists.newArrayList(parent.getGenotypes());

        for (ReadContextCounter counter : counters) {
            Genotype genotype = createGenotype(false, counter);
            genotypes.add(genotype);
        }
        return builder.genotypes(genotypes).make();
    }

    @NotNull
    public static VariantContext create(@NotNull final SageVariant entry) {
        final List<Genotype> genotypes = Lists.newArrayList();
        for (int i = 0; i < entry.normalAltContexts().size(); i++) {
            ReadContextCounter normalContext = entry.normalAltContexts().get(i);
            genotypes.add(createGenotype(i == 0, entry.variant(), normalContext));
        }

        entry.tumorAltContexts().stream().map(x -> createGenotype(false, entry.variant(), x)).forEach(genotypes::add);
        return createContext(entry, genotypes);
    }

    @NotNull
    private static VariantContext createContext(@NotNull final SageVariant variant, @NotNull final List<Genotype> genotypes) {
        final VariantContextBuilder builder = CandidateSerialization.toContext(variant.candidate())
                .log10PError(variant.totalQuality() / -10d)
                .genotypes(genotypes)
                .filters(variant.filters());

        if (variant.localPhaseSet() > 0) {
            builder.attribute(LOCAL_PHASE_SET, variant.localPhaseSet());
        }

        if (variant.localRealignSet() > 0) {
            builder.attribute(LOCAL_REALIGN_SET, variant.localRealignSet());
        }

        if (variant.mixedGermlineImpact() > 0) {
            builder.attribute(MIXED_SOMATIC_GERMLINE, variant.mixedGermlineImpact());
        }

        if (variant.phasedInframeIndel() > 0) {
            builder.attribute(PHASED_INFRAME_INDEL, variant.phasedInframeIndel());
        }

        if (variant.isRealigned()) {
            builder.attribute(RIGHT_ALIGNED_MICROHOMOLOGY, true);
        }

        final VariantContext context = builder.make();
        if (context.isNotFiltered()) {
            context.getCommonInfo().addFilter(PASS);
        }

        return context;
    }

    @NotNull
    private static Genotype createGenotype(boolean germline, @NotNull final ReadContextCounter counter) {
        return createGenotype(germline, counter.variant(), counter);
    }

    @NotNull
    private static Genotype createGenotype(boolean germline, @NotNull final VariantHotspot variant,
            @NotNull final ReadContextCounter counter) {
        return new GenotypeBuilder(counter.sample()).DP(counter.depth())
                .AD(new int[] { counter.refSupport(), counter.altSupport() })
                .attribute(READ_CONTEXT_QUALITY, counter.quality())
                .attribute(READ_CONTEXT_COUNT, counter.counts())
                .attribute(READ_CONTEXT_IMPROPER_PAIR, counter.improperPair())
                .attribute(READ_CONTEXT_JITTER, counter.jitter())
                .attribute(RAW_ALLELIC_DEPTH, new int[] { counter.rawRefSupport(), counter.rawAltSupport() })
                .attribute(RAW_ALLELIC_BASE_QUALITY, new int[] { counter.rawRefBaseQuality(), counter.rawAltBaseQuality() })
                .attribute(RAW_DEPTH, counter.rawDepth())
                .attribute(VCFConstants.ALLELE_FREQUENCY_KEY, counter.vaf())
                .alleles(createGenotypeAlleles(germline, variant, counter))
                .make();
    }

    @NotNull
    private static List<Allele> createGenotypeAlleles(boolean germline, @NotNull final VariantHotspot variant,
            @NotNull final ReadContextCounter counter) {
        final Allele ref = Allele.create(variant.ref(), true);
        final Allele alt = Allele.create(variant.alt(), false);

        if (germline && Doubles.lessOrEqual(counter.refAllelicFrequency(), HET_CUTOFF)) {
            return Lists.newArrayList(alt, alt);
        }

        if (germline && Doubles.lessOrEqual(counter.vaf(), HET_CUTOFF)) {
            return Lists.newArrayList(ref, ref);
        }

        return Lists.newArrayList(ref, alt);
    }
}
