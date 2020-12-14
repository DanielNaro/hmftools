package com.hartwig.hmftools.serve.extraction.gene;

import static com.hartwig.hmftools.common.drivercatalog.DriverCategory.ONCO;
import static com.hartwig.hmftools.common.drivercatalog.DriverCategory.TSG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.drivercatalog.panel.ImmutableDriverGene;
import com.hartwig.hmftools.common.serve.classification.EventType;
import com.hartwig.hmftools.serve.extraction.util.GeneChecker;
import com.hartwig.hmftools.serve.extraction.util.GeneCheckerTestFactory;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class GeneLevelExtractorTest {

    private static final GeneChecker V37_GENE_CHECKER = GeneCheckerTestFactory.buildForV37();

    @Test
    public void canExtractGeneLevelEventONCO() {
        GeneLevelExtractor geneLevelExtractor = createWithDriverGenes(createDriverGenes("STK11", "KIT"));
        GeneLevelAnnotation geneLevelEvent = geneLevelExtractor.extract("KIT", EventType.GENE_LEVEL, "KIT  positive");

        assertNotNull(geneLevelEvent);
        assertEquals("KIT", geneLevelEvent.gene());
        assertEquals(GeneLevelEvent.ACTIVATION, geneLevelEvent.event());
    }

    @Test
    public void canExtractGeneLevelEventTSG() {
        GeneLevelExtractor geneLevelExtractor = createWithDriverGenes(createDriverGenes("STK11", "KIT"));
        GeneLevelAnnotation geneLevelEvent = geneLevelExtractor.extract("TP53", EventType.GENE_LEVEL, "TP53  negative");

        assertNotNull(geneLevelEvent);
        assertEquals("TP53", geneLevelEvent.gene());
        assertEquals(GeneLevelEvent.INACTIVATION, geneLevelEvent.event());
    }

    @Test
    public void canExtractGeneLevelEventGeneral() {
        GeneLevelExtractor geneLevelExtractor = createWithDriverGenes(createDriverGenes("STK11", "MET"));
        GeneLevelAnnotation geneLevelEvent = geneLevelExtractor.extract("STK11", EventType.GENE_LEVEL, "Truncating Mutations");

        assertNotNull(geneLevelEvent);
        assertEquals("STK11", geneLevelEvent.gene());
        assertEquals(GeneLevelEvent.INACTIVATION, geneLevelEvent.event());
    }

    @Test
    public void canExtractGeneLevelEventFusion() {
        GeneLevelExtractor geneLevelExtractor = createWithDriverGenes(createDriverGenes("STK11", "MET"));
        GeneLevelAnnotation geneLevelEvent = geneLevelExtractor.extract("NTRK3", EventType.PROMISCUOUS_FUSION, "NTRK3 fusion");

        assertNotNull(geneLevelEvent);
        assertEquals("NTRK3", geneLevelEvent.gene());
        assertEquals(GeneLevelEvent.FUSION, geneLevelEvent.event());
    }

    @Test
    public void filtersNonExistingGenes() {
        GeneLevelExtractor geneLevelExtractor = createWithDriverGenes(createDriverGenes("STK11", "MET"));
        assertNull(geneLevelExtractor.extract("NOT-A-GENE", EventType.PROMISCUOUS_FUSION, "NTRK3 fusion"));
    }

    @Test
    public void canExtractGeneLevelEvent() {
        GeneLevelExtractor geneLevelExtractor = createWithDriverGenes(createDriverGenes("NOTCH1", "MET"));

        assertEquals(GeneLevelEvent.ACTIVATION, geneLevelExtractor.extractGeneLevelEvent("KRAS", "KRAS activating mutation"));
        assertEquals(GeneLevelEvent.ACTIVATION, geneLevelExtractor.extractGeneLevelEvent("KRAS", "KRAS act mut"));
        assertEquals(GeneLevelEvent.INACTIVATION, geneLevelExtractor.extractGeneLevelEvent("NOTCH1", "LOSS-OF-FUNCTION"));
        assertEquals(GeneLevelEvent.INACTIVATION, geneLevelExtractor.extractGeneLevelEvent("NOTCH1", "inact mut"));

        assertEquals(GeneLevelEvent.ACTIVATION, geneLevelExtractor.extractGeneLevelEvent("MET", "MUTATION"));
        assertEquals(GeneLevelEvent.INACTIVATION, geneLevelExtractor.extractGeneLevelEvent("NOTCH1", "MUTATION"));
        assertEquals(GeneLevelEvent.INACTIVATION, geneLevelExtractor.extractGeneLevelEvent("NOTCH1", "NOTCH1 "));
        assertEquals(GeneLevelEvent.ANY_MUTATION, geneLevelExtractor.extractGeneLevelEvent("BRCA1", "BRCA1"));
        assertEquals(GeneLevelEvent.ANY_MUTATION, geneLevelExtractor.extractGeneLevelEvent("KRAS", "not a gene level event"));
    }

    @Test
    public void canDetermineGeneLevelFromDriverGenes() {
        List<DriverGene> driverGenes = createDriverGenes("STK11", "MET");

        assertEquals(GeneLevelEvent.ACTIVATION, GeneLevelExtractor.determineGeneLevelEventFromDriverGenes(driverGenes, "MET"));
        assertEquals(GeneLevelEvent.INACTIVATION, GeneLevelExtractor.determineGeneLevelEventFromDriverGenes(driverGenes, "STK11"));
        assertEquals(GeneLevelEvent.ANY_MUTATION, GeneLevelExtractor.determineGeneLevelEventFromDriverGenes(driverGenes, "MAP1K1"));
    }

    @NotNull
    private static GeneLevelExtractor createWithDriverGenes(@NotNull List<DriverGene> driverGenes) {
        return new GeneLevelExtractor(V37_GENE_CHECKER, V37_GENE_CHECKER,
                driverGenes,
                Sets.newHashSet("positive", "activating mutation", "act mut"),
                Sets.newHashSet("negative", "LOSS-OF-FUNCTION", "inact mut"));
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