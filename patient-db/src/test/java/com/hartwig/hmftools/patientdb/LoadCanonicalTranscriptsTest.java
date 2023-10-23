package com.hartwig.hmftools.patientdb;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.junit.Test;

import static com.hartwig.hmftools.patientdb.LoadCanonicalTranscripts.getRefGenomeStr;
import static org.junit.Assert.assertEquals;

public class LoadCanonicalTranscriptsTest{

    @Test
    public void testGetRefGenomeStr() {
        assertEquals("GRCh37", getRefGenomeStr(RefGenomeVersion.V37));
        assertEquals("GRCh38", getRefGenomeStr(RefGenomeVersion.V38));
    }
}