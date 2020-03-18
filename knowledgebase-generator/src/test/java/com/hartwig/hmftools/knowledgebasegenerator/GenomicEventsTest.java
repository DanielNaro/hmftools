package com.hartwig.hmftools.knowledgebasegenerator;

import static org.junit.Assert.*;

import org.junit.Test;

public class GenomicEventsTest {

    @Test
    public void canDetermineGenomicEvent() {
        assertEquals(GenomicEvents.AMPLIFICATION, GenomicEvents.genomicEvents("Amplification"));
        assertEquals(GenomicEvents.DELETION, GenomicEvents.genomicEvents("Deletion"));
        assertEquals(GenomicEvents.VARIANT, GenomicEvents.genomicEvents("Variants"));
        assertEquals(GenomicEvents.RANGE, GenomicEvents.genomicEvents("Range"));
        assertEquals(GenomicEvents.FUSION, GenomicEvents.genomicEvents("Fusions"));
        assertEquals(GenomicEvents.SIGNATURE, GenomicEvents.genomicEvents("Signatures"));
        assertEquals(GenomicEvents.SIGNATURE_MSI, GenomicEvents.genomicEvents("MSI"));
        assertEquals(GenomicEvents.SIGNATURE_HRD, GenomicEvents.genomicEvents("HRD"));
        assertEquals(GenomicEvents.SIGNATURE_MTL, GenomicEvents.genomicEvents("MTL"));
        assertEquals(GenomicEvents.SIGNATURE_MTB, GenomicEvents.genomicEvents("MTB"));

    }
}