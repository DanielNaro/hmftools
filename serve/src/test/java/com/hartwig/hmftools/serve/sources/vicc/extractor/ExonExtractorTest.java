package com.hartwig.hmftools.serve.sources.vicc.extractor;

import static com.hartwig.hmftools.common.drivercatalog.DriverCategory.ONCO;
import static com.hartwig.hmftools.common.drivercatalog.DriverCategory.TSG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.drivercatalog.panel.ImmutableDriverGene;
import com.hartwig.hmftools.common.genome.genepanel.HmfGenePanelSupplier;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;
import com.hartwig.hmftools.common.serve.classification.EventType;
import com.hartwig.hmftools.serve.actionability.range.MutationTypeFilter;
import com.hartwig.hmftools.serve.exon.ExonAnnotation;
import com.hartwig.hmftools.serve.extraction.GeneChecker;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ExonExtractorTest {

    private static final Map<String, HmfTranscriptRegion> HG19_GENE_MAP = HmfGenePanelSupplier.allGenesMap37();
    private static final GeneChecker HG19_GENE_CHECKER = new GeneChecker(HG19_GENE_MAP.keySet());

    @Test
    public void canExtractExonForExonAndFusion() {
        ExonExtractor extractor = createWithDriverGenes(createDriverGenes("TP53", "KIT"));
        List<ExonAnnotation> exons = extractor.extract("KIT", null, EventType.FUSION_PAIR_AND_EXON, "EXON 11 MUTATION");

        assertEquals(1, exons.size());

        assertEquals("4", exons.get(0).chromosome());
        assertEquals(55593577, exons.get(0).start());
        assertEquals(55593713, exons.get(0).end());
        assertEquals("KIT", exons.get(0).gene());
        assertEquals(MutationTypeFilter.MISSENSE_ANY, exons.get(0).mutationType());
    }

    @Test
    public void canExtractExonForwardStrand() {
        ExonExtractor extractor = createWithDriverGenes(createDriverGenes("TP53", "EGFR"));
        List<ExonAnnotation> exons = extractor.extract("EGFR", null, EventType.EXON, "EXON 19 DELETION");

        assertEquals(1, exons.size());

        assertEquals("7", exons.get(0).chromosome());
        assertEquals(55242410, exons.get(0).start());
        assertEquals(55242518, exons.get(0).end());
        assertEquals("EGFR", exons.get(0).gene());
        assertEquals(MutationTypeFilter.MISSENSE_INFRAME_DELETION, exons.get(0).mutationType());
    }

    @Test
    public void canExtractExonReverseStrand() {
        ExonExtractor extractor = createWithDriverGenes(createDriverGenes("TP53", "EGFR"));
        List<ExonAnnotation> exons = extractor.extract("KRAS", null, EventType.EXON, "EXON 2 DELETION");

        assertEquals(1, exons.size());

        assertEquals("12", exons.get(0).chromosome());
        assertEquals(25398203, exons.get(0).start());
        assertEquals(25398334, exons.get(0).end());
        assertEquals("KRAS", exons.get(0).gene());
        assertEquals(MutationTypeFilter.MISSENSE_INFRAME_DELETION, exons.get(0).mutationType());
    }

    @Test
    public void canFilterOnNonCanonicalTranscript() {
        ExonExtractor extractor = createWithDriverGenes(createDriverGenes("TP53", "EGFR"));
        assertNull(extractor.extract("KRAS", "not the canonical transcript", EventType.EXON, "EXON 2 DELETION"));
    }

    @Test
    public void canFilterWhenExonIndicesDoNotExist() {
        ExonExtractor extractor = createWithDriverGenes(createDriverGenes("TP53", "EGFR"));
        assertNull(extractor.extract("KRAS", "ENST00000256078", EventType.EXON, "not a correct event"));
    }

    @Test
    public void canFilterWhenExonIndexNotOnTranscript() {
        ExonExtractor extractor = createWithDriverGenes(createDriverGenes("TP53", "EGFR"));
        assertNull(extractor.extract("KRAS", "ENST00000256078", EventType.EXON, "Exon 2000 deletion"));
    }

    @Test
    public void canExtractExonIndices() {
        assertEquals(Lists.newArrayList(19), ExonExtractor.extractExonIndices("EGFR exon 19 insertions"));
        assertEquals(Lists.newArrayList(20), ExonExtractor.extractExonIndices("ERBB2 proximal exon 20"));
        assertEquals(Lists.newArrayList(9, 11, 13, 14, 17), ExonExtractor.extractExonIndices("KIT mutation in exon 9,11,13,14 or 17"));
        assertEquals(Lists.newArrayList(16, 17, 18, 19), ExonExtractor.extractExonIndices("MET mutation in exon 16-19"));
        assertEquals(Lists.newArrayList(2, 3), ExonExtractor.extractExonIndices("Null (Partial deletion of Exons 2 & 3)"));
        assertEquals(Lists.newArrayList(12), ExonExtractor.extractExonIndices("Exon 12 splice site insertion"));

        assertNull(ExonExtractor.extractExonIndices("Not an exon number"));
    }

    @NotNull
    private static ExonExtractor createWithDriverGenes(@NotNull List<DriverGene> driverGenes) {
        return new ExonExtractor(HG19_GENE_CHECKER, new MutationTypeFilterAlgo(driverGenes), HG19_GENE_MAP);
    }

    @NotNull
    private static List<DriverGene> createDriverGenes(@NotNull String geneTsg, @NotNull String geneOnco) {
        ImmutableDriverGene.Builder driverGeneBuilder = ImmutableDriverGene.builder()
                .reportMissenseAndInframe(false)
                .reportNonsenseAndFrameshift(false)
                .reportSplice(false)
                .reportDeletion(false)
                .reportDisruption(false)
                .reportAmplification(false)
                .reportSomaticHotspot(false)
                .reportGermlineVariant(false)
                .reportGermlineHotspot(false);

        DriverGene driverGeneTsg = driverGeneBuilder.gene(geneTsg).likelihoodType(TSG).build();
        DriverGene driverGeneOnco = driverGeneBuilder.gene(geneOnco).likelihoodType(ONCO).build();

        return Lists.newArrayList(driverGeneTsg, driverGeneOnco);
    }
}