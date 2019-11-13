package com.hartwig.hmftools.common.slicing;

import java.io.IOException;

import com.hartwig.hmftools.common.region.BEDFileLoader;

import org.jetbrains.annotations.NotNull;

public final class SlicerFactory {

    private SlicerFactory() {
    }

    @NotNull
    public static Slicer fromBedFile(@NotNull String bedFile) throws IOException {
        return new BidirectionalSlicer(BEDFileLoader.fromBedFile(bedFile));
    }
}
