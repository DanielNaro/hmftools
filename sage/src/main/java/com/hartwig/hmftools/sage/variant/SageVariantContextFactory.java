package com.hartwig.hmftools.sage.variant;

import static com.hartwig.hmftools.sage.vcf.SageVCF.PASS;
import static com.hartwig.hmftools.sage.vcf.SageVCF.PHASE;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RAW_ALLELIC_BASE_QUALITY;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RAW_ALLELIC_DEPTH;
import static com.hartwig.hmftools.sage.vcf.SageVCF.RAW_DEPTH;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_COUNT;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_DIFFERENCE;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_DISTANCE;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_IMPROPER_PAIR;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_JITTER;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_MICRO_HOMOLOGY;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_QUALITY;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_REPEAT_COUNT;
import static com.hartwig.hmftools.sage.vcf.SageVCF.READ_CONTEXT_REPEAT_SEQUENCE;
import static com.hartwig.hmftools.sage.vcf.SageVCF.TIER;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
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
    public static VariantContext germlineOnly(@NotNull final SageVariant entry) {
        final ReadContextCounter normal = entry.primaryNormal();

        final Genotype normalGenotype = createGenotype(true, normal);
        final List<Genotype> genotypes = Collections.singletonList(normalGenotype);
        return createContext(entry, createAlleles(normal), genotypes, normal);
    }

    @NotNull
    public static VariantContext pairedTumorNormal(@NotNull final SageVariant entry) {
        final List<ReadContextCounter> tumorContexts = entry.tumorAltContexts();

        assert (tumorContexts.size() >= 1);

        final ReadContextCounter firstTumor = tumorContexts.get(0);

        final List<Genotype> genotypes = Lists.newArrayList();
        for (int i = 0; i < entry.normalAltContexts().size(); i++) {
            ReadContextCounter normalContext = entry.normalAltContexts().get(i);
            genotypes.add(createGenotype(i == 0, normalContext));
        }

        tumorContexts.stream().map(x -> createGenotype(false, x)).forEach(genotypes::add);

        return createContext(entry, createAlleles(entry.primaryNormal()), genotypes, firstTumor);
    }

    @NotNull
    private static VariantContext createContext(@NotNull final SageVariant variant, @NotNull final List<Allele> alleles,
            @NotNull final List<Genotype> genotypes, @NotNull final ReadContextCounter counter) {
        final VariantContextBuilder builder = new VariantContextBuilder().chr(variant.chromosome())
                .start(variant.position())
                .attribute(READ_CONTEXT, counter.readContext().toString())
                .attribute(READ_CONTEXT_DIFFERENCE, counter.readContext().distanceCigar())
                .attribute(READ_CONTEXT_DISTANCE, counter.readContext().distance())
                .log10PError(variant.totalQuality() / -10d)
                .source("SAGE")
                .computeEndFromAlleles(alleles, (int) variant.position())
                .attribute(TIER, variant.tier())
                .genotypes(genotypes)
                .alleles(alleles)
                .filters(variant.filters());

        if (!counter.readContext().microhomology().isEmpty()) {
            builder.attribute(READ_CONTEXT_MICRO_HOMOLOGY, counter.readContext().microhomology());
        }

        if (counter.readContext().repeatCount() > 0) {
            builder.attribute(READ_CONTEXT_REPEAT_COUNT, counter.readContext().repeatCount())
                    .attribute(READ_CONTEXT_REPEAT_SEQUENCE, counter.readContext().repeat());
        }

        if (variant.localPhaseSet() > 0) {
            builder.attribute(PHASE, variant.localPhaseSet());
        }

        final VariantContext context = builder.make();
        if (context.isNotFiltered()) {
            context.getCommonInfo().addFilter(PASS);
        }

        return context;
    }

    @NotNull
    private static Genotype createGenotype(boolean germline, @NotNull final ReadContextCounter counter) {
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
                .alleles(createGenotypeAlleles(germline, counter))
                .make();
    }

    @NotNull
    private static List<Allele> createAlleles(@NotNull final VariantHotspot variant) {
        final Allele ref = Allele.create(variant.ref(), true);
        final Allele alt = Allele.create(variant.alt(), false);
        return Lists.newArrayList(ref, alt);
    }

    @NotNull
    private static List<Allele> createGenotypeAlleles(boolean germline, @NotNull final ReadContextCounter counter) {
        final Allele ref = Allele.create(counter.ref(), true);
        final Allele alt = Allele.create(counter.alt(), false);

        if (germline && Doubles.lessOrEqual(counter.refAllelicFrequency(), HET_CUTOFF)) {
            return Lists.newArrayList(alt, alt);
        }

        if (germline && Doubles.lessOrEqual(counter.vaf(), HET_CUTOFF)) {
            return Lists.newArrayList(ref, ref);
        }

        return Lists.newArrayList(ref, alt);
    }

}
