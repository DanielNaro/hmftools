package com.hartwig.hmftools.common.immune;

import com.hartwig.hmftools.common.region.ChrBaseRegion;

import java.util.List;

public interface ImmuneRegionInterface {
    List<ChrBaseRegion> getIgRegions();
    List<ChrBaseRegion> getTrRegions();
    ChrBaseRegion getIgRegion(final String geneName);
}
