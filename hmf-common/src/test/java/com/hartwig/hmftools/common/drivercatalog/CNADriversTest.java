package com.hartwig.hmftools.common.drivercatalog;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.copynumber.CopyNumberMethod;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.common.purple.gene.ImmutableGeneCopyNumber;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class CNADriversTest {

    @Test
    public void testDeletionsInGermlineAsStillReportable() {
        final GeneCopyNumber del = createTestCopyNumberBuilder("APC").germlineHet2HomRegions(1).germlineHomRegions(1).build();
        List<DriverCatalog> drivers = new CNADrivers().deletions(Lists.newArrayList(del));
        assertEquals(1, drivers.size());
        assertEquals("APC", drivers.get(0).gene());
    }

    @Test
    public void testChromosomeBand() {
        GeneCopyNumber mapped = createTestCopyNumberBuilder("AC093642.5").build();
        GeneCopyNumber unmapped = createTestCopyNumberBuilder("APC").build();

        List<DriverCatalog> drivers = new CNADrivers().deletions(Lists.newArrayList(mapped, unmapped));
        assertEquals(2, drivers.size());
        assertEquals("q telomere", drivers.get(0).chromosomeBand());
        assertEquals("band", drivers.get(1).chromosomeBand());
    }

    @NotNull
    private static ImmutableGeneCopyNumber.Builder createTestCopyNumberBuilder(@NotNull final String gene) {
        return ImmutableGeneCopyNumber.builder()
                .start(1)
                .end(2)
                .gene(gene)
                .chromosome("1")
                .chromosomeBand("band")
                .minRegionStart(0)
                .minRegionStartSupport(SegmentSupport.NONE)
                .minRegionEnd(0)
                .minRegionEndSupport(SegmentSupport.NONE)
                .minRegionMethod(CopyNumberMethod.UNKNOWN)
                .minRegions(1)
                .germlineHet2HomRegions(0)
                .germlineHomRegions(0)
                .somaticRegions(1)
                .minCopyNumber(0.1)
                .maxCopyNumber(0.1)
                .transcriptID("trans")
                .transcriptVersion(0)
                .minMinorAllelePloidy(0);
    }
}
