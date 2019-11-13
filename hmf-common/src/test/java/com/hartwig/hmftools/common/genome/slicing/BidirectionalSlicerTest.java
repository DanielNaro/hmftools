package com.hartwig.hmftools.common.genome.slicing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.genome.region.GenomeRegions;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantTestBuilderFactory;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

public class BidirectionalSlicerTest {

    private BidirectionalSlicer slicer;

    @Before
    public void setup() {
        final SortedSetMultimap<String, GenomeRegion> regionMap = TreeMultimap.create();
        regionMap.put("X", GenomeRegions.create("X", 100, 200));
        regionMap.put("X", GenomeRegions.create("X", 300, 400));
        regionMap.put("Y", GenomeRegions.create("Y", 500, 600));

        slicer = new BidirectionalSlicer(regionMap);
    }

    @Test
    public void excludedChromosomes() {
        assertFalse(slicer.includes(buildVariant("1", 0)));
        assertFalse(slicer.includes(buildVariant("NotExists", 100)));
    }

    @Test
    public void sortedVariants() {
        assertFalse(slicer.includes(buildVariant("X", 1)));
        assertTrue(slicer.includes(buildVariant("X", 150)));
        assertFalse(slicer.includes(buildVariant("X", 250)));
        assertTrue(slicer.includes(buildVariant("X", 300)));
        assertTrue(slicer.includes(buildVariant("X", 400)));
        assertTrue(slicer.includes(buildVariant("Y", 570)));
        assertFalse(slicer.includes(buildVariant("Y", 1000)));
        assertFalse(slicer.includes(buildVariant("NotExists", 100)));
    }

    @Test
    public void bidirectionalSlicerWithUnsortedVariants() {
        assertTrue(slicer.includes(buildVariant("X", 150)));
        assertTrue(slicer.includes(buildVariant("X", 300)));
        assertTrue(slicer.includes(buildVariant("X", 400)));
        assertTrue(slicer.includes(buildVariant("Y", 570)));
        assertFalse(slicer.includes(buildVariant("X", 1)));
        assertFalse(slicer.includes(buildVariant("X", 250)));
        assertFalse(slicer.includes(buildVariant("Y", 1000)));
        assertFalse(slicer.includes(buildVariant("NotExists", 100)));
    }

    @NotNull
    private static SomaticVariant buildVariant(@NotNull String chromosome, long position) {
        return SomaticVariantTestBuilderFactory.create().chromosome(chromosome).position(position).build();
    }
}