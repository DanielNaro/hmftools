package com.hartwig.hmftools.common.immune;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.region.ChrBaseRegion;

import java.util.List;

public class ImmuneRegion_V38 implements ImmuneRegionInterface{
    public static final ChrBaseRegion IGK_REGION_V38 = new ChrBaseRegion("chr2", 88857161, 90315836);
    public static final ChrBaseRegion IGH_REGION_V38 = new ChrBaseRegion("chr14", 105586437, 106879844);
    public static final ChrBaseRegion IGL_REGION_V38 = new ChrBaseRegion("chr22", 22026076, 22922913);


    public static final ChrBaseRegion TRG_REGION_V38 = new ChrBaseRegion("chr7", 38239580, 38367882);
    public static final ChrBaseRegion TRB_REGION_V38 = new ChrBaseRegion("chr7", 142299177, 142812869);
    public static final ChrBaseRegion TRAD_REGION_V38 = new ChrBaseRegion("chr14", 21622293, 22552156);


    public static final List<ChrBaseRegion> IG_REGIONS_V38 = Lists.newArrayList(IGK_REGION_V38, IGH_REGION_V38, IGL_REGION_V38);
    public static final List<ChrBaseRegion> TR_REGIONS_V38 = Lists.newArrayList(TRG_REGION_V38, TRB_REGION_V38, TRAD_REGION_V38);

    public List<ChrBaseRegion> getIgRegions()
    {
        return IG_REGIONS_V38;
    }

    public List<ChrBaseRegion> getTrRegions()
    {
        return TR_REGIONS_V38;
    }

    public ChrBaseRegion getIgRegion(final String geneName)
    {
        switch (geneName) {
            case "IGK":
                return IGK_REGION_V38;
            case "IGH":
                return IGH_REGION_V38;
            case "IGL":
                return IGL_REGION_V38;
            default:
                return null;
        }
    }
}
