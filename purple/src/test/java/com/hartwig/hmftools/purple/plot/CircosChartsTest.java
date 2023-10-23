package com.hartwig.hmftools.purple.plot;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CircosChartsTest {


    @Test
    public void testGetKaryotypePath() {
        assertEquals("data/karyotype/karyotype.human.hg19.txt", CircosCharts.getKaryotypePath(RefGenomeVersion.V37));
        assertEquals("data/karyotype/karyotype.human.hg38.txt", CircosCharts.getKaryotypePath(RefGenomeVersion.V38));
    }

    @Test
    public void testGetCircosGapsPath() {
        assertEquals("gaps.37.txt", CircosCharts.getCircosGapsPath(RefGenomeVersion.V37));
        assertEquals("gaps.38.txt", CircosCharts.getCircosGapsPath(RefGenomeVersion.V38));
    }

}