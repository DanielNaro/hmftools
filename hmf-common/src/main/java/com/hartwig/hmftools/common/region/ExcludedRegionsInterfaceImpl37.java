package com.hartwig.hmftools.common.region;

import com.google.common.collect.Lists;

import java.util.List;

public class ExcludedRegionsInterfaceImpl37 implements ExcludedRegionsInterface {
    static ChrBaseRegion EXCLUDED_REGION_1_REF_37 = new ChrBaseRegion("2",
            33141260, 33141700);
    public static final List<ChrBaseRegion> POLY_G_REGIONS_V37 = Lists.newArrayList();
    static {
        POLY_G_REGIONS_V37.add(EXCLUDED_REGION_1_REF_37);
        POLY_G_REGIONS_V37.add(new ChrBaseRegion("4", 41218427, 41218467));
        POLY_G_REGIONS_V37.add(new ChrBaseRegion("17", 42646418, 42646458));
    }
    @Override
    public ChrBaseRegion getPolyGRegion() {
        return EXCLUDED_REGION_1_REF_37;
    }

    @Override
    public List<ChrBaseRegion> getPolyGRegions() {
        return POLY_G_REGIONS_V37;
    }
}
