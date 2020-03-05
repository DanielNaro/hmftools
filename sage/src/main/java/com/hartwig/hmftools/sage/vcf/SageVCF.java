package com.hartwig.hmftools.sage.vcf;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.enrich.SomaticRefContextEnrichment;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.config.SoftFilter;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;

public class SageVCF implements AutoCloseable {

    public final static String READ_CONTEXT = "RC";
    public final static String PASS = "PASS";
    public final static String DEDUP_FILTER = "dedup";
    public final static String MIN_GERMLINE_VAF = "min_germline_vaf";

    private final static String READ_CONTEXT_DESCRIPTION = "Read context";
    public final static String READ_CONTEXT_JITTER = "RC_JIT";
    private final static String READ_CONTEXT_JITTER_DESCRIPTION = "Read context jitter [Shortened, Lengthened, QualityPenalty]";

    public final static String READ_CONTEXT_COUNT = "RC_CNT";
    private final static String READ_CONTEXT_COUNT_DESCRIPTION = "Read context counts [Full, Partial, Core, Realigned, Reference, Total]";
    public static final String READ_CONTEXT_REPEAT_COUNT = "RC_REPC";
    private static final String READ_CONTEXT_REPEAT_COUNT_DESCRIPTION = "Repeat count at read context";
    public static final String READ_CONTEXT_REPEAT_SEQUENCE = "RC_REPS";
    private static final String READ_CONTEXT_REPEAT_SEQUENCE_DESCRIPTION = "Repeat sequence at read context";
    public static final String READ_CONTEXT_MICRO_HOMOLOGY = "RC_MH";
    private static final String READ_CONTEXT_MICRO_HOMOLOGY_DESCRIPTION = "Micro-homology at read context";
    public static final String READ_CONTEXT_QUALITY = "RC_QUAL";
    private static final String READ_CONTEXT_QUALITY_DESCRIPTION =
            "Read context quality [Full, Partial, Core, Realigned, Reference, Total]";
    private static final String READ_CONTEXT_AF_DESCRIPTION =
            "Allelic frequency calculated from read context counts as (Full + Partial + Realigned) / Coverage";

    public static final String READ_CONTEXT_DISTANCE = "RC_DIS";
    private static final String READ_CONTEXT_DISTANCE_DESCRIPTION = "Distance from read context to ref sequence";
    public static final String READ_CONTEXT_DIFFERENCE = "RC_DIF";
    private static final String READ_CONTEXT_DIFFERENCE_DESCRIPTION = "Difference between read context and ref sequence";
    public static final String READ_CONTEXT_IMPROPER_PAIR = "RC_IPC";
    private static final String READ_CONTEXT_IMPROPER_PAIR_DESCRIPTION = "Read context improper pair count";

    public static final String RAW_DEPTH = "RDP";
    public static final String RAW_ALLELIC_DEPTH = "RAD";
    public static final String RAW_ALLELIC_BASE_QUALITY = "RABQ";

    public final static String TIER = "TIER";
    private final static String TIER_DESCRIPTION = "Tier: [HOTSPOT, PANEL, HIGH_CONFIDENCE, LOW_CONFIDENCE]";
    public final static String PHASE = "LPS";
    private final static String PHASE_DESCRIPTION = "Local Phase Set";

    private final VariantContextWriter writer;
    private final SomaticRefContextEnrichment refContextEnrichment;

    public SageVCF(@NotNull final IndexedFastaSequenceFile reference, @NotNull final SageConfig config) {
        writer = new VariantContextWriterBuilder().setOutputFile(config.outputFile())
                .modifyOption(Options.INDEX_ON_THE_FLY, true)
                .modifyOption(Options.USE_ASYNC_IO, false)
                .setReferenceDictionary(reference.getSequenceDictionary())
                .build();
        refContextEnrichment = new SomaticRefContextEnrichment(reference, this::writeToFile);

        final VCFHeader header = refContextEnrichment.enrichHeader(header(config));
        header.setSequenceDictionary(reference.getSequenceDictionary());
        writer.writeHeader(header);
    }

    public void addVCF(@NotNull final String filename) throws IOException {
        try (final AbstractFeatureReader<VariantContext, LineIterator> reader = getFeatureReader(filename, new VCFCodec(), false)) {
            for (VariantContext context : reader.iterator()) {
                refContextEnrichment.accept(context);
            }
        }
    }

    private void writeToFile(@NotNull final VariantContext context) {
        writer.add(context);
    }

    @NotNull
    static VCFHeader header(@NotNull final SageConfig config) {
        final List<String> samples = Lists.newArrayList(config.reference());
        if (!config.germlineOnly()) {
            samples.addAll(config.tumor());
        }
        return header(config.version(), samples);
    }

    @NotNull
    private static VCFHeader header(@NotNull final String version, @NotNull final List<String> allSamples) {

        VCFHeader header = new VCFHeader(Collections.emptySet(), allSamples);
        header.addMetaDataLine(new VCFHeaderLine("sageVersion", version));
        header.addMetaDataLine(VCFStandardHeaderLines.getFormatLine((VCFConstants.GENOTYPE_KEY)));
        header.addMetaDataLine(VCFStandardHeaderLines.getFormatLine((VCFConstants.GENOTYPE_ALLELE_DEPTHS)));
        header.addMetaDataLine(VCFStandardHeaderLines.getFormatLine((VCFConstants.DEPTH_KEY)));
        header.addMetaDataLine(new VCFInfoHeaderLine(VCFConstants.ALLELE_FREQUENCY_KEY,
                1,
                VCFHeaderLineType.Float,
                READ_CONTEXT_AF_DESCRIPTION));

        header.addMetaDataLine(new VCFFormatHeaderLine(READ_CONTEXT_JITTER, 3, VCFHeaderLineType.Integer, READ_CONTEXT_JITTER_DESCRIPTION));
        header.addMetaDataLine(new VCFFormatHeaderLine(RAW_ALLELIC_DEPTH, 2, VCFHeaderLineType.Integer, "Raw allelic depth"));
        header.addMetaDataLine(new VCFFormatHeaderLine(RAW_ALLELIC_BASE_QUALITY, 2, VCFHeaderLineType.Integer, "Raw allelic base quality"));
        header.addMetaDataLine(new VCFFormatHeaderLine(RAW_DEPTH, 1, VCFHeaderLineType.Integer, "Raw read depth"));
        header.addMetaDataLine(new VCFFormatHeaderLine(READ_CONTEXT_COUNT, 6, VCFHeaderLineType.Integer, READ_CONTEXT_COUNT_DESCRIPTION));
        header.addMetaDataLine(new VCFFormatHeaderLine(READ_CONTEXT_IMPROPER_PAIR,
                1,
                VCFHeaderLineType.Integer,
                READ_CONTEXT_IMPROPER_PAIR_DESCRIPTION));
        header.addMetaDataLine(new VCFFormatHeaderLine(READ_CONTEXT_QUALITY,
                6,
                VCFHeaderLineType.Integer,
                READ_CONTEXT_QUALITY_DESCRIPTION));

        header.addMetaDataLine(new VCFInfoHeaderLine(READ_CONTEXT, 1, VCFHeaderLineType.String, READ_CONTEXT_DESCRIPTION));
        header.addMetaDataLine(new VCFInfoHeaderLine(READ_CONTEXT_DIFFERENCE,
                1,
                VCFHeaderLineType.String,
                READ_CONTEXT_DIFFERENCE_DESCRIPTION));
        header.addMetaDataLine(new VCFInfoHeaderLine(READ_CONTEXT_DISTANCE,
                1,
                VCFHeaderLineType.Integer,
                READ_CONTEXT_DISTANCE_DESCRIPTION));
        header.addMetaDataLine(new VCFInfoHeaderLine(READ_CONTEXT_REPEAT_COUNT,
                1,
                VCFHeaderLineType.Integer,
                READ_CONTEXT_REPEAT_COUNT_DESCRIPTION));
        header.addMetaDataLine(new VCFInfoHeaderLine(READ_CONTEXT_REPEAT_SEQUENCE,
                1,
                VCFHeaderLineType.String,
                READ_CONTEXT_REPEAT_SEQUENCE_DESCRIPTION));
        header.addMetaDataLine(new VCFInfoHeaderLine(READ_CONTEXT_MICRO_HOMOLOGY,
                1,
                VCFHeaderLineType.String,
                READ_CONTEXT_MICRO_HOMOLOGY_DESCRIPTION));

        header.addMetaDataLine(new VCFInfoHeaderLine(PHASE, 1, VCFHeaderLineType.Integer, PHASE_DESCRIPTION));
        header.addMetaDataLine(new VCFInfoHeaderLine(TIER, 1, VCFHeaderLineType.String, TIER_DESCRIPTION));

        header.addMetaDataLine(new VCFFilterHeaderLine(DEDUP_FILTER, "Variant was removed as duplicate"));
        header.addMetaDataLine(new VCFFilterHeaderLine(MIN_GERMLINE_VAF, "Insufficient germline VAF"));

        header.addMetaDataLine(new VCFFilterHeaderLine(SoftFilter.MIN_TUMOR_QUAL.toString(), "Insufficient tumor quality"));
        header.addMetaDataLine(new VCFFilterHeaderLine(SoftFilter.MIN_TUMOR_VAF.toString(), "Insufficient tumor VAF"));
        header.addMetaDataLine(new VCFFilterHeaderLine(SoftFilter.MIN_GERMLINE_DEPTH.toString(), "Insufficient germline depth"));
        header.addMetaDataLine(new VCFFilterHeaderLine(SoftFilter.MAX_GERMLINE_VAF.toString(), "Excess germline VAF"));
        header.addMetaDataLine(new VCFFilterHeaderLine(SoftFilter.MAX_GERMLINE_REL_RAW_BASE_QUAL.toString(),
                "Excess germline relative quality"));
        header.addMetaDataLine(new VCFFilterHeaderLine(SoftFilter.MAX_GERMLINE_ALT_SUPPORT.toString(), "Excess germline alt support"));
        header.addMetaDataLine(new VCFFilterHeaderLine(PASS, "All filters passed"));

        return header;
    }

    @Override
    public void close() {
        writer.close();
    }

}
