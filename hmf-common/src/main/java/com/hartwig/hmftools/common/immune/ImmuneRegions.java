package com.hartwig.hmftools.common.immune;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.region.ChrBaseRegion;

import java.util.List;

public final class ImmuneRegions
{

    public static List<ChrBaseRegion> getIgRegions(final RefGenomeVersion refGenomeVersion)
    {
        return refGenomeVersion.getImmuneRegions().getIgRegions();
    }

    public static List<ChrBaseRegion> getTrRegions(final RefGenomeVersion refGenomeVersion)
    {
        return refGenomeVersion.getImmuneRegions().getTrRegions();
    }

    public static ChrBaseRegion getIgRegion(final String geneName, final RefGenomeVersion refGenomeVersion)
    {
        return refGenomeVersion.getImmuneRegions().getIgRegion(geneName);
    }

}
