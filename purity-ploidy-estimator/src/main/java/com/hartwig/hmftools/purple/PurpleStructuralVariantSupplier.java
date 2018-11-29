package com.hartwig.hmftools.purple;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantFactory;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextComparator;
import htsjdk.variant.variantcontext.filter.PassingVariantFilter;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class PurpleStructuralVariantSupplier {

    private static final String RECOVERED_FLAG = "RECOVERED";
    private static final String INFERRED_FLAG = "INFERRED";

    private static final Allele REF_ALLELE = Allele.create("N", true);
    private static final Allele INCREASING_ALLELE = Allele.create(".N", false);
    private static final Allele DECREASING_ALLELE = Allele.create("N.", false);

    private final String outputVCF;
    private final Optional<VCFHeader> header;
    private final TreeSet<VariantContext> variantContexts;
    private final List<StructuralVariant> variants = Lists.newArrayList();
    private boolean modified = true;

    private int counter = 0;

    PurpleStructuralVariantSupplier() {
        header = Optional.empty();
        variantContexts = new TreeSet<>();
        outputVCF = Strings.EMPTY;
    }

    PurpleStructuralVariantSupplier(@NotNull final String templateVCF, @NotNull final String outputVCF) {
        final VCFFileReader vcfReader = new VCFFileReader(new File(templateVCF), false);
        this.outputVCF = outputVCF;
        header = Optional.of(generateOutputHeader(vcfReader.getFileHeader()));
        variantContexts = new TreeSet<>(new VCComparator(header.get().getSequenceDictionary()));
        for (VariantContext context : vcfReader) {
            if (context.isNotFiltered()) {
                variantContexts.add(context);
            }
        }

        vcfReader.close();
    }

    public void recoverVariant(@NotNull VariantContext variantContext) {
        modified = true;
        final VariantContext unfiltered = new VariantContextBuilder(variantContext).unfiltered().attribute(RECOVERED_FLAG, true).make();
        variantContexts.add(unfiltered);
    }

    public void inferMissingVariant(@NotNull List<PurpleCopyNumber> copyNumbers) {
        for (int i = 1; i < copyNumbers.size(); i++) {
            PurpleCopyNumber copyNumber = copyNumbers.get(i);
            if (copyNumber.segmentStartSupport() == SegmentSupport.NONE) {
                final PurpleCopyNumber prev = copyNumbers.get(i - 1);
                variantContexts.add(infer(copyNumber, prev));
            }
        }
    }

    @NotNull
    private VariantContext infer(@NotNull final PurpleCopyNumber copyNumber, @NotNull final PurpleCopyNumber prev) {

        final long position;
        final Allele allele;
        if (Doubles.greaterThan(copyNumber.averageTumorCopyNumber(), prev.averageTumorCopyNumber())) {
            allele = INCREASING_ALLELE;
            position = copyNumber.start();
        } else {
            allele = DECREASING_ALLELE;
            position = copyNumber.start() - 1;
        }

        final Collection<Allele> alleles = Lists.newArrayList(REF_ALLELE, allele);

        long lowerRange = Math.min(-500, copyNumber.minStart() - copyNumber.start());
        long upperRange = Math.max(500, copyNumber.maxStart() - copyNumber.start());

        String cipos = lowerRange + "," + upperRange;

        return new VariantContextBuilder("purple", copyNumber.chromosome(), position, copyNumber.start(), alleles).filter(INFERRED_FLAG)
                .attribute("IMPRECISE", true)
                .id("purple_" + counter++)
                .attribute("CIPOS", cipos)
                .attribute("SVTYPE", "BND")
                .noGenotypes()
                .make();
    }

    public void write() {
        if (header.isPresent()) {
            VariantContextWriter writer = new VariantContextWriterBuilder().setOutputFile(outputVCF)
                    .setReferenceDictionary(header.get().getSequenceDictionary())
                    .setIndexCreator(new TabixIndexCreator(header.get().getSequenceDictionary(), new TabixFormat()))
                    .setOutputFileType(VariantContextWriterBuilder.OutputType.BLOCK_COMPRESSED_VCF)
                    .setOption(Options.ALLOW_MISSING_FIELDS_IN_HEADER)
                    .build();

            writer.writeHeader(header.get());
            variantContexts.forEach(writer::add);
            writer.close();
        }
    }

    @NotNull
    public List<StructuralVariant> variants() {
        if (modified) {
            modified = false;
            final StructuralVariantFactory factory = new StructuralVariantFactory(new PassingVariantFilter());
            variantContexts.forEach(factory::addVariantContext);

            variants.clear();
            variants.addAll(factory.results());
        }

        return variants;
    }

    @NotNull
    private static VCFHeader generateOutputHeader(@NotNull final VCFHeader template) {
        final VCFHeader outputVCFHeader = new VCFHeader(template.getMetaDataInInputOrder(), template.getSampleNamesInOrder());
        outputVCFHeader.addMetaDataLine(new VCFInfoHeaderLine(RECOVERED_FLAG, 0, VCFHeaderLineType.Flag, "Entry has been recovered"));

        return outputVCFHeader;
    }

    private class VCComparator extends VariantContextComparator {

        VCComparator(final SAMSequenceDictionary dictionary) {
            super(dictionary);
        }

        @Override
        public int compare(final VariantContext firstVariantContext, final VariantContext secondVariantContext) {
            int positionResult = super.compare(firstVariantContext, secondVariantContext);

            return positionResult == 0 ? firstVariantContext.getID().compareTo(secondVariantContext.getID()) : positionResult;
        }
    }

}
