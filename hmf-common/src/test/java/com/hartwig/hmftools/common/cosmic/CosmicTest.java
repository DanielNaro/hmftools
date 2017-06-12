package com.hartwig.hmftools.common.cosmic;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.google.common.io.Resources;
import com.hartwig.hmftools.common.exception.EmptyFileException;

import org.junit.Test;

public class CosmicTest {

    private static final String COSMIC_EXAMPLE_FILE =
            Resources.getResource("cosmic").getPath() + File.separator + "CosmicExample.csv";

    @Test
    public void canLoadFromCsv() throws IOException, EmptyFileException {
        final CosmicModel cosmicModel = Cosmic.buildModelFromCsv(COSMIC_EXAMPLE_FILE);
        final Map<String, CosmicData> dataPerSample = cosmicModel.data();
        assertEquals(7, dataPerSample.size());

        final CosmicData gene1Data = cosmicModel.data().get("ABI1");
        assertEquals(gene1Data, cosmicModel.data().get("ABI-1"));
        assertEquals("abl-interactor 1", gene1Data.description());
        assertEquals("10006", gene1Data.entrezId());
        assertEquals("10:27037499-27149792", gene1Data.genomeLocation());
        assertEquals("10p11.2", gene1Data.chromosomeBand());
        assertEquals("yes", gene1Data.somatic());
        assertEquals("", gene1Data.germline());
        assertEquals("AML", gene1Data.somaticTumorTypes());
        assertEquals("", gene1Data.germlineTumorTypes());
        assertEquals("", gene1Data.cancerSyndrome());
        assertEquals("L", gene1Data.tissueType());
        assertEquals("Dom", gene1Data.molecularGenetics());
        assertEquals("T", gene1Data.mutationTypes());
        assertEquals("KMT2A", gene1Data.translocationPartner());
        assertEquals("", gene1Data.otherGermlineMut());
        assertEquals("", gene1Data.otherSyndrome());
        final CosmicData gene2Data = cosmicModel.data().get("ACKR3");
        assertEquals("atypical chemokine receptor 3", gene2Data.description());
        assertEquals("57007", gene2Data.entrezId());
        assertEquals("2:-", gene2Data.genomeLocation());
        assertEquals("2q37.3", gene2Data.chromosomeBand());
        assertEquals("", gene2Data.somatic());
        assertEquals("", gene2Data.germline());
        assertEquals("", gene2Data.somaticTumorTypes());
        assertEquals("", gene2Data.germlineTumorTypes());
        assertEquals("", gene2Data.cancerSyndrome());
        assertEquals("", gene2Data.tissueType());
        assertEquals("", gene2Data.molecularGenetics());
        assertEquals("", gene2Data.mutationTypes());
        assertEquals("", gene2Data.translocationPartner());
        assertEquals("", gene2Data.otherGermlineMut());
        assertEquals("", gene2Data.otherSyndrome());
    }
}
