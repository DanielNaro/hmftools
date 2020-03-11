package com.hartwig.hmftools.common.variant.enrich;

import static com.hartwig.hmftools.common.variant.hotspot.VariantHotspotFile.readFromVCF;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.variant.clonality.PeakModel;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;

public class VariantContextEnrichmentPurple implements VariantContextEnrichment {

    private final VariantContextEnrichment purityEnrichment;
    private final VariantContextEnrichment hotspotEnrichment;
    private final VariantContextEnrichment kataegisEnrichment;
    private final VariantContextEnrichment somaticRefContextEnrichment;
    private final VariantContextEnrichment subclonalLikelihoodEnrichment;

    public VariantContextEnrichmentPurple(boolean hotspotEnabled, double clonalityMaxPloidy, double clonalityBinWidth,
            @NotNull final String purpleVersion, @NotNull final String tumorSample, @NotNull final IndexedFastaSequenceFile reference,
            @NotNull final PurityAdjuster purityAdjuster, @NotNull final List<PurpleCopyNumber> copyNumbers,
            @NotNull final List<FittedRegion> fittedRegions, @NotNull final List<PeakModel> peakModel, @NotNull final String hotspots,
            @NotNull final Consumer<VariantContext> consumer) throws IOException {
        subclonalLikelihoodEnrichment = new SubclonalLikelihoodEnrichment(clonalityMaxPloidy, clonalityBinWidth, peakModel, consumer);
        purityEnrichment =
                new PurityEnrichment(purpleVersion, tumorSample, purityAdjuster, copyNumbers, fittedRegions, subclonalLikelihoodEnrichment);
        kataegisEnrichment = new KataegisEnrichment(purityEnrichment);
        somaticRefContextEnrichment = new SomaticRefContextEnrichment(reference, kataegisEnrichment);
        if (hotspotEnabled) {
            hotspotEnrichment = new VariantHotspotEnrichment(readFromVCF(hotspots), somaticRefContextEnrichment);
        } else {
            hotspotEnrichment = VariantContextEnrichmentFactory.noEnrichment().create(somaticRefContextEnrichment);
        }
    }

    @Override
    public void flush() {
        hotspotEnrichment.flush();
        somaticRefContextEnrichment.flush();
        kataegisEnrichment.flush();
        purityEnrichment.flush();
        subclonalLikelihoodEnrichment.flush();
    }

    @NotNull
    @Override
    public VCFHeader enrichHeader(@NotNull final VCFHeader template) {
        VCFHeader header = somaticRefContextEnrichment.enrichHeader(template);
        header = kataegisEnrichment.enrichHeader(header);
        header = subclonalLikelihoodEnrichment.enrichHeader(header);
        header = hotspotEnrichment.enrichHeader(header);
        return purityEnrichment.enrichHeader(header);
    }

    @Override
    public void accept(@NotNull final VariantContext context) {
        hotspotEnrichment.accept(context);
    }
}
