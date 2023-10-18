package com.hartwig.hmftools.common.region;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ExcludedRegionsTest {
    @Test
    public void testGetPolyGRegion_V37() {
        ChrBaseRegion EXCLUDED_REGION_1_REF_37 = new ChrBaseRegion("2", 33141260
                , 33141700);

        assertEquals(EXCLUDED_REGION_1_REF_37,
                ExcludedRegions.getPolyGRegion(RefGenomeVersion.V37));
    }

    @Test
    public void testGetPolyGRegion_V38() {
        ChrBaseRegion EXCLUDED_REGION_1_REF_38 = new ChrBaseRegion("chr2", 32916190, 32916630);

        assertEquals(EXCLUDED_REGION_1_REF_38,
                ExcludedRegions.getPolyGRegion(RefGenomeVersion.V38));
    }

    @Test
    public void testGetPolyGRegions_V37() {
        List<ChrBaseRegion> POLY_G_REGIONS_V37 = Lists.newArrayList();
        ChrBaseRegion EXCLUDED_REGION_1_REF_37 = new ChrBaseRegion("2",
                33141260, 33141700);
        POLY_G_REGIONS_V37.add(EXCLUDED_REGION_1_REF_37);
        POLY_G_REGIONS_V37.add(new ChrBaseRegion("4", 41218427, 41218467));
        POLY_G_REGIONS_V37.add(new ChrBaseRegion("17", 42646418, 42646458));

        assertEquals(POLY_G_REGIONS_V37,
                ExcludedRegions.getPolyGRegions(RefGenomeVersion.V37));
    }

    @Test
    public void testGetPolyGRegions_V38() {
        List<ChrBaseRegion> POLY_G_REGIONS_V38 = Lists.newArrayList();
        ChrBaseRegion EXCLUDED_REGION_1_REF_38 = new ChrBaseRegion("chr2", 32916190, 32916630);
        POLY_G_REGIONS_V38.add(EXCLUDED_REGION_1_REF_38);
        POLY_G_REGIONS_V38.add(new ChrBaseRegion("chr4", 41216410, 41216450));
        POLY_G_REGIONS_V38.add(new ChrBaseRegion("chr17", 44569050, 44569090));

        assertEquals(POLY_G_REGIONS_V38,
                ExcludedRegions.getPolyGRegions(RefGenomeVersion.V38));
    }
}