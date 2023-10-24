package com.hartwig.hmftools.lilac;


import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReferenceDataTest{

    @Test
    public void testGetPonIndelsRefFile() {
        assertEquals("/pon/indels_v37.csv",
                ReferenceData.getPonIndelsRefFile(RefGenomeVersion.V37));
        assertEquals("/pon/indels_v38.csv",
                ReferenceData.getPonIndelsRefFile(RefGenomeVersion.V38));
    }
}