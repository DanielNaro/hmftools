package com.hartwig.hmftools.pave;

import static com.hartwig.hmftools.common.variant.PaveVcfTags.GNOMAD_FREQ;
import static com.hartwig.hmftools.pave.PaveConfig.PV_LOGGER;
import static com.hartwig.hmftools.pave.annotation.PonAnnotation.PON_COUNT;
import static com.hartwig.hmftools.pave.annotation.PonAnnotation.PON_MAX;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.utils.version.VersionInfo;
import com.hartwig.hmftools.common.variant.impact.VariantImpact;
import com.hartwig.hmftools.common.variant.impact.VariantImpactSerialiser;
import com.hartwig.hmftools.common.variant.impact.VariantTranscriptImpact;
import com.hartwig.hmftools.pave.annotation.Blacklistings;
import com.hartwig.hmftools.pave.annotation.ClinvarAnnotation;
import com.hartwig.hmftools.pave.annotation.GnomadAnnotation;
import com.hartwig.hmftools.pave.annotation.Mappability;
import com.hartwig.hmftools.pave.annotation.PonAnnotation;
import com.hartwig.hmftools.pave.annotation.Reportability;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;

public class VcfWriter
{
    private final VCFFileReader mHeader;
    private final VariantContextWriter mWriter;

    private final Map<HumanChromosome,List<VariantContext>> mChrPendingVariants;
    private final Set<HumanChromosome> mCompleteChromosomes;
    private HumanChromosome mCurrentChromosome;

    public static final String PASS = "PASS";

    public VcfWriter(final String outputVCF, final String templateVCF)
    {
        mHeader = new VCFFileReader(new File(templateVCF), false);

        mWriter = new VariantContextWriterBuilder()
                .setReferenceDictionary(mHeader.getFileHeader().getSequenceDictionary())
                .setOutputFile(outputVCF)
                .setOutputFileType(VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF)
                .build();

        mChrPendingVariants = Maps.newHashMap();
        mCompleteChromosomes = Sets.newHashSet();
        mCurrentChromosome = HumanChromosome._1;

        for(HumanChromosome chromosome : HumanChromosome.values())
        {
            mChrPendingVariants.put(chromosome, Lists.newArrayList());
        }
    }

    public final void writeHeader(final ReferenceData referenceData, boolean setReportability)
    {
        final VersionInfo version = new VersionInfo("pave.version");

        VCFHeader newHeader = new VCFHeader(mHeader.getFileHeader());
        newHeader.addMetaDataLine(new VCFHeaderLine("PaveVersion", version.version()));

        VariantTranscriptImpact.writeHeader(newHeader);
        VariantImpactSerialiser.writeHeader(newHeader);

        if(referenceData.StandardPon.isEnabled())
        {
            PonAnnotation.addHeader(newHeader);
        }

        if(referenceData.Gnomad.hasData())
        {
            GnomadAnnotation.addHeader(newHeader);
        }

        if(referenceData.VariantMappability.hasData()   )
        {
            Mappability.addHeader(newHeader);
        }

        if(referenceData.Clinvar.hasData())
        {
            ClinvarAnnotation.addHeader(newHeader);
        }

        if(referenceData.BlacklistedVariants.hasData())
        {
            Blacklistings.addHeader(newHeader);
        }

        if(setReportability)
            Reportability.addHeader(newHeader);

        mWriter.writeHeader(newHeader);
    }

    public static VariantContext buildVariant(final VariantContext context, final VariantData variant, final VariantImpact variantImpact)
    {
        VariantContextBuilder builder = new VariantContextBuilder(variant.context())
                .genotypes(variant.context().getGenotypes())
                .filters(context.getFilters());

        if(!variant.filters().isEmpty())
        {
            builder.getFilters().addAll(variant.filters());
            builder.getFilters().remove(PASS);
        }

        if(builder.getFilters().isEmpty())
            builder.getFilters().add(PASS);

        VariantContext newContext = builder.make();

        if(!variant.getImpacts().isEmpty())
        {
            List<VariantTranscriptImpact> transImpacts = Lists.newArrayList();

            for(Map.Entry<String, List<VariantTransImpact>> entry : variant.getImpacts().entrySet())
            {
                final String geneName = entry.getKey();
                final List<VariantTransImpact> geneImpacts = entry.getValue();

                for(VariantTransImpact transImpact : geneImpacts)
                {
                    transImpacts.add(new VariantTranscriptImpact(
                            transImpact.TransData.GeneId, geneName, transImpact.TransData.TransName,
                            transImpact.effectsStr(), transImpact.inSpliceRegion(),
                            transImpact.hgvsCoding(), transImpact.hgvsProtein()));
                }
            }

            VariantTranscriptImpact.writeVcfData(newContext, transImpacts);

            if(variantImpact != null)
                VariantImpactSerialiser.writeImpactDetails(newContext, variantImpact);
        }

        if(variant.gnomadFrequency() != null && !newContext.getCommonInfo().hasAttribute(GNOMAD_FREQ))
            newContext.getCommonInfo().putAttribute(GNOMAD_FREQ, variant.gnomadFrequency());

        if((variant.ponSampleCount() > 0 || variant.ponMaxReadCount() > 0) && !newContext.getCommonInfo().hasAttribute(PON_COUNT))
        {
            newContext.getCommonInfo().putAttribute(PON_COUNT, variant.ponSampleCount());
            newContext.getCommonInfo().putAttribute(PON_MAX, variant.ponMaxReadCount());
        }

        return newContext;
    }

    public synchronized void onChromosomeComplete(final HumanChromosome chromosome)
    {
        mCompleteChromosomes.add(chromosome);
        writePendingVariants();
    }

    public synchronized void writeVariant(final HumanChromosome chromosome, final VariantContext variantContext)
    {
        if(mCurrentChromosome == chromosome)
        {
            mWriter.add(variantContext);
            return;
        }

        List<VariantContext> pendingVariants = mChrPendingVariants.get(chromosome);

        if(pendingVariants == null)
        {
            pendingVariants = Lists.newArrayList();
            mChrPendingVariants.put(chromosome, pendingVariants);
        }

        pendingVariants.add(variantContext);
    }

    private void writePendingVariants()
    {
        for(HumanChromosome chromosome : HumanChromosome.values())
        {
            if(!mCompleteChromosomes.contains(chromosome))
            {
                mCurrentChromosome = chromosome;
                break;
            }

            List<VariantContext> pendingVariants = mChrPendingVariants.get(chromosome);

            if(pendingVariants == null)
                continue;

            if(!pendingVariants.isEmpty())
            {
                PV_LOGGER.debug("chr({}) writing {} pending variants", chromosome, pendingVariants.size());

                pendingVariants.forEach(x -> mWriter.add(x));
                pendingVariants.clear();
            }

            mChrPendingVariants.remove(chromosome);
        }
    }

    public void close()
    {
        mWriter.close();
    }
}
