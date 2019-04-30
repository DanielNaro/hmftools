package com.hartwig.hmftools.patientreporter.variants.somatic;

import java.util.List;

import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.SomaticVariant;

import org.jetbrains.annotations.NotNull;

public final class MutationalLoadAnalyzer {

    private MutationalLoadAnalyzer() {
    }

    static int determineTumorMutationalLoad(@NotNull List<? extends SomaticVariant> variants) {
        int variantsWhichCountToMutationalLoad = 0;
        for (final SomaticVariant variant : variants) {
            if (countsTowardsMutationalLoad(variant)) {
                variantsWhichCountToMutationalLoad++;
            }
        }
        return variantsWhichCountToMutationalLoad;
    }

    private static boolean countsTowardsMutationalLoad(@NotNull SomaticVariant variant) {
        return !variant.isFiltered() && variant.worstCodingEffect() == CodingEffect.MISSENSE;
    }
}
