package com.hartwig.hmftools.serve.hotspot.tools;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import com.hartwig.hmftools.common.drivercatalog.panel.DriverGenePanel;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGenePanelFactory;
import com.hartwig.hmftools.common.genome.region.CanonicalTranscript;
import com.hartwig.hmftools.common.genome.region.CanonicalTranscriptFactory;
import com.hartwig.hmftools.common.variant.CanonicalAnnotation;
import com.hartwig.hmftools.common.variant.snpeff.SnpEffAnnotation;
import com.hartwig.hmftools.common.variant.snpeff.SnpEffAnnotationFactory;
import com.hartwig.hmftools.serve.util.AminoAcidFunctions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;

public class AnnotatedHotspotVCFPrinter {

    private static final Logger LOGGER = LogManager.getLogger(AnnotatedHotspotVCFPrinter.class);

    public static void main(String[] args) throws IOException {
        String annotatedInputVcf = System.getProperty("user.home") + "/hmf/tmp/annotatedHotspots.vcf";
        new AnnotatedHotspotVCFPrinter().run(annotatedInputVcf);
    }

    public void run(@NotNull String annotatedInputVcf) throws IOException {
        DriverGenePanel genePanel = DriverGenePanelFactory.empty();
        List<CanonicalTranscript> canonicalTranscripts = CanonicalTranscriptFactory.create37();
        CanonicalAnnotation factory = new CanonicalAnnotation(genePanel.driverGenes(), canonicalTranscripts);

        LOGGER.info("Simplifying variants from '{}'", annotatedInputVcf);
        AbstractFeatureReader<VariantContext, LineIterator> reader = getFeatureReader(annotatedInputVcf, new VCFCodec(), false);
        for (VariantContext variant : reader.iterator()) {
            List<SnpEffAnnotation> annotations = SnpEffAnnotationFactory.fromContext(variant);
            Optional<SnpEffAnnotation> canonical = factory.canonicalSnpEffAnnotation(annotations);

            String canonicalProtein = canonical.map(SnpEffAnnotation::hgvsProtein).orElse(Strings.EMPTY);
            if (canonicalProtein.isEmpty()) {
                canonicalProtein = "-";
            }

            StringJoiner joiner = new StringJoiner("|");
            joiner.add(variant.getContig())
                    .add(String.valueOf(variant.getStart()))
                    .add(variant.getAlleles().get(0).getBaseString())
                    .add(variant.getAlleles().get(1).getBaseString())
                    .add(canonical.map(SnpEffAnnotation::gene).orElse(Strings.EMPTY))
                    .add(canonical.map(SnpEffAnnotation::transcript).orElse(Strings.EMPTY))
                    .add(canonical.map(SnpEffAnnotation::consequenceString).orElse(Strings.EMPTY))
                    .add(canonical.map(SnpEffAnnotation::hgvsCoding).orElse(Strings.EMPTY))
                    .add(AminoAcidFunctions.forceSingleLetterProteinAnnotation(canonicalProtein));

            Object input = variant.getAttribute("input");
            if (input != null) {
                joiner.add(input.toString());
            }

            List<String> sources = variant.getAttributeAsStringList("sources", Strings.EMPTY);
            if (!sources.isEmpty()) {
                StringJoiner sourceJoiner = new StringJoiner(",");
                for (String source : sources) {
                    sourceJoiner.add(source);
                }
                joiner.add(sourceJoiner.toString());
            }

            System.out.println(joiner.toString());
        }
    }
}
