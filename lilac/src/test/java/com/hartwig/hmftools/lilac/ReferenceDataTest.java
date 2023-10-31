package com.hartwig.hmftools.lilac;


import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.lilac.read.Indel;
import org.junit.Test;

import static com.hartwig.hmftools.lilac.ReferenceData.*;
import static org.junit.Assert.assertEquals;

public class ReferenceDataTest{

    @Test
    public void testGetPonIndelsRefFile() {
        assertEquals("/pon/indels_v37.csv",
                ReferenceData.getPonIndelsRefFile(RefGenomeVersion.V37));
        assertEquals("/pon/indels_v38.csv",
                ReferenceData.getPonIndelsRefFile(RefGenomeVersion.V38));
    }

    @Test
    public void testSetKnownStopLossIndels_V37() {
        setKnownStopLossIndels(RefGenomeVersion.V37);
        assertEquals(
                new Indel("6", 31237115, "CN", "C"),
                STOP_LOSS_ON_C_INDEL
        );

        assertEquals(
                "6",
                LilacConstants.HLA_CHR
        );
    }

    @Test
    public void testSetKnownStopLossIndels_V38() {
        setKnownStopLossIndels(RefGenomeVersion.V38);
        assertEquals(
                new Indel("chr6", 31269338, "CN", "C"),
                STOP_LOSS_ON_C_INDEL
        );
        assertEquals(
                "chr6",
                LilacConstants.HLA_CHR
        );
    }

    public void testGetHlaTranscriptsFile() {
        assertEquals("", getHlaTranscriptsFile(RefGenomeVersion.V37) );
        assertEquals("", getHlaTranscriptsFile(RefGenomeVersion.V38) );
    }
}