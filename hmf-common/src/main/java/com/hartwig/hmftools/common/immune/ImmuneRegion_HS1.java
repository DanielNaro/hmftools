package com.hartwig.hmftools.common.immune;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.region.ChrBaseRegion;

import java.util.List;

public class ImmuneRegion_HS1 implements ImmuneRegionInterface{
    //Obtained from https://www.ncbi.nlm.nih.gov/gene/50802
    public static final ChrBaseRegion IGK_REGION_HS1 = new ChrBaseRegion("chr2"
            , 88866370, 90790947);
    //Obtained from https://www.ncbi.nlm.nih.gov/gene/3492
    public static final ChrBaseRegion IGH_REGION_HS1 = new ChrBaseRegion(
            "chr14", 99839469, 101155136);
    //Obtained from https://www.ncbi.nlm.nih.gov/gene/3535
    public static final ChrBaseRegion IGL_REGION_HS1 = new ChrBaseRegion(
            "chr22", 22439629, 23345823);


    //Obtained from https://www.ncbi.nlm.nih.gov/gene/6965
    public static final ChrBaseRegion TRG_REGION_HS1 = new ChrBaseRegion(
            "chr7", 38380942, 38524936);
    //Obtained from https://www.ncbi.nlm.nih.gov/gene/6957
    public static final ChrBaseRegion TRB_REGION_HS1 = new ChrBaseRegion("chr7"
            , 143614080, 144169005);
    //Obtained from https://www.ncbi.nlm.nih.gov/gene/6964
    public static final ChrBaseRegion TRAD_REGION_HS1 = new ChrBaseRegion(
            "chr14", 16620292, 16664335);


    public static final List<ChrBaseRegion> IG_REGIONS_HS1 =
            Lists.newArrayList(IGK_REGION_HS1, IGH_REGION_HS1, IGL_REGION_HS1);
    public static final List<ChrBaseRegion> TR_REGIONS_HS1 =
            Lists.newArrayList(TRG_REGION_HS1, TRB_REGION_HS1, TRAD_REGION_HS1);

    public List<ChrBaseRegion> getIgRegions()
    {
        return IG_REGIONS_HS1;
    }

    public List<ChrBaseRegion> getTrRegions()
    {
        return TR_REGIONS_HS1;
    }

    public ChrBaseRegion getIgRegion(final String geneName)
    {
        switch (geneName) {
            case "IGK":
                return IGK_REGION_HS1;
            case "IGH":
                return IGH_REGION_HS1;
            case "IGL":
                return IGL_REGION_HS1;
            default:
                return null;
        }
    }
}
