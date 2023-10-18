package com.hartwig.hmftools.common.immune;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.region.ChrBaseRegion;

import java.util.List;

public class ImmuneRegion_V37 implements ImmuneRegionInterface{
    public static final ChrBaseRegion IGK_REGION_V37 = new ChrBaseRegion("2", 89156674, 90538397);
    public static final ChrBaseRegion IGH_REGION_V37 = new ChrBaseRegion("14", 106032614, 107288051);
    public static final ChrBaseRegion IGL_REGION_V37 = new ChrBaseRegion("22", 22380474, 23265085);

    public static final ChrBaseRegion TRG_REGION_V37 = new ChrBaseRegion("7", 38279181, 38407483);
    public static final ChrBaseRegion TRB_REGION_V37 = new ChrBaseRegion("7", 141999017, 142510554);
    public static final ChrBaseRegion TRAD_REGION_V37 = new ChrBaseRegion("14", 22090446, 23021099);

    public static final List<ChrBaseRegion> IG_REGIONS_V37 = Lists.newArrayList(IGK_REGION_V37, IGH_REGION_V37, IGL_REGION_V37);

    public static final List<ChrBaseRegion> TR_REGIONS_V37 = Lists.newArrayList(TRG_REGION_V37, TRB_REGION_V37, TRAD_REGION_V37);

    public List<ChrBaseRegion> getIgRegions()
    {
        return IG_REGIONS_V37;
    }

    public List<ChrBaseRegion> getTrRegions()
    {
        return TR_REGIONS_V37;
    }

    public ChrBaseRegion getIgRegion(final String geneName)
    {
        switch (geneName) {
            case "IGK":
                return IGK_REGION_V37;
            case "IGH":
                return IGH_REGION_V37;
            case "IGL":
                return IGL_REGION_V37;
            default:
                return null;
        }
    }
}
