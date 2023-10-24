package com.hartwig.hmftools.bamtools.metrics;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MetricsConfigTest {

    @Test
    public void testGetUnmappableRegionsFileName() {
        assertEquals("/genome_unmappable_regions.37.bed",
                MetricsConfig.getUnmappableRegionsFileName(RefGenomeVersion.V37));
        assertEquals("/genome_unmappable_regions.38.bed",
                MetricsConfig.getUnmappableRegionsFileName(RefGenomeVersion.V38));
    }
}