package com.hartwig.hmftools.common.region;

import com.google.common.collect.Lists;

import java.util.List;

public class ExcludedRegionsInterfaceImpl38 implements ExcludedRegionsInterface {
    public static final ChrBaseRegion EXCLUDED_REGION_1_REF_38 = new ChrBaseRegion("chr2", 32916190, 32916630);
    public static final List<ChrBaseRegion> POLY_G_REGIONS_V38 = Lists.newArrayList();
    static
    {
        POLY_G_REGIONS_V38.add(EXCLUDED_REGION_1_REF_38);
        POLY_G_REGIONS_V38.add(new ChrBaseRegion("chr4", 41216410, 41216450));
        POLY_G_REGIONS_V38.add(new ChrBaseRegion("chr17", 44569050, 44569090));
    }
    @Override
    public ChrBaseRegion getPolyGRegion() {
        return EXCLUDED_REGION_1_REF_38;
    }

    @Override
    public List<ChrBaseRegion> getPolyGRegions() {
        return POLY_G_REGIONS_V38;
    }
}
