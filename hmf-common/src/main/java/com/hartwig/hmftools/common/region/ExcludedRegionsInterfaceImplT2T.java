package com.hartwig.hmftools.common.region;

import com.google.common.collect.Lists;

import java.util.List;

public class ExcludedRegionsInterfaceImplT2T implements ExcludedRegionsInterface {
    public static final ChrBaseRegion EXCLUDED_REGION_1_REF_T2T =
            new ChrBaseRegion("chr2", 41997388, 41997513);
    public static final List<ChrBaseRegion> POLY_G_REGIONS_T2T = Lists.newArrayList();
    static
    {
        POLY_G_REGIONS_T2T.add(EXCLUDED_REGION_1_REF_T2T);
        POLY_G_REGIONS_T2T.add(new ChrBaseRegion("chr4", 41190205, 41190251));
        POLY_G_REGIONS_T2T.add(new ChrBaseRegion("chr17", 45422961, 45422998));
    }
    @Override
    public ChrBaseRegion getPolyGRegion() {
        return EXCLUDED_REGION_1_REF_T2T;
    }

    @Override
    public List<ChrBaseRegion> getPolyGRegions() {
        return POLY_G_REGIONS_T2T;
    }
}
