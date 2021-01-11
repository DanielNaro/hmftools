package com.hartwig.hmftools.common.purple.region;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hartwig.hmftools.common.genome.chromosome.CobaltChromosomes;
import com.hartwig.hmftools.common.genome.chromosome.CobaltChromosomesTest;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.genome.region.GenomeRegions;

import org.junit.Test;

public class FittedRegionFactoryTest {

    private final CobaltChromosomes male = CobaltChromosomesTest.male();
    private final CobaltChromosomes female = CobaltChromosomesTest.female();

    @Test
    public void testFitYChromosome() {
        final GenomeRegion region = GenomeRegions.create("Y", 1, 100);
        assertTrue(FittedRegionFactoryV2.isAllowedRegion(male, region));
        assertFalse(FittedRegionFactoryV2.isAllowedRegion(female, region));
    }

    @Test
    public void testFitXChromosome() {
        final GenomeRegion region = GenomeRegions.create("X", 1, 100);
        assertTrue(FittedRegionFactoryV2.isAllowedRegion(male, region));
        assertTrue(FittedRegionFactoryV2.isAllowedRegion(female, region));
    }
}
