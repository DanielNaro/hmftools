package com.hartwig.hmftools.common.region;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;

import java.util.List;

public final class ExcludedRegions
{
    public static final String POLY_G_INSERT = "GGGGGGGGGGGGGGGG";
    public static final String POLY_C_INSERT = "CCCCCCCCCCCCCCCC";
    public static final int POLY_G_LENGTH = POLY_G_INSERT.length();

    public static ChrBaseRegion getPolyGRegion(final RefGenomeVersion refGenomeVersion)
    {
        return refGenomeVersion.getExcludedRegionsInterface().getPolyGRegion();
    }


    public static List<ChrBaseRegion> getPolyGRegions(final RefGenomeVersion refGenomeVersion)
    {
        return refGenomeVersion.getExcludedRegionsInterface().getPolyGRegions();
    }

}
