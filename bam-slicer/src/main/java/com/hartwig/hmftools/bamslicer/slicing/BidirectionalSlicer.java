package com.hartwig.hmftools.bamslicer.slicing;

import java.util.Collection;

import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.genome.position.GenomePosition;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;

import org.jetbrains.annotations.NotNull;

class BidirectionalSlicer implements Slicer {

    @NotNull
    private final Multimap<String, ? extends GenomeRegion> regions;

    BidirectionalSlicer(@NotNull final Multimap<String, ? extends GenomeRegion> regions) {
        this.regions = regions;
    }

    @Override
    public boolean test(@NotNull GenomePosition variant) {
        Collection<? extends GenomeRegion> regionsForChrom = regions.get(variant.chromosome());
        if (regionsForChrom == null) {
            return false;
        } else {
            for (GenomeRegion region : regionsForChrom) {
                if (variant.position() >= region.start() && variant.position() <= region.end()) {
                    return true;
                } else if (region.start() > variant.position()) {
                    return false;
                }
            }
        }

        return false;
    }

    @NotNull
    @Override
    public Collection<? extends GenomeRegion> regions() {
        return regions.values();
    }
}
