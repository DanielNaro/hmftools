package com.hartwig.hmftools.common.variant.hotspot;

import com.hartwig.hmftools.common.genome.position.GenomePosition;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface VariantHotspot extends GenomePosition {

    @NotNull
    String ref();

    @NotNull
    String alt();

    default long end() {
        return position() + ref().length() - 1;
    }

    default boolean isSNV() {
        return ref().length() == 1 && alt().length() == 1;
    }

    default boolean isMNV() {
        return ref().length() == alt().length() && ref().length() != 1;
    }

    default boolean isSimpleInsert() {
        return ref().length() == 1 && alt().length() > 1 && ref().charAt(0) == alt().charAt(0);
    }

    default boolean isSimpleDelete() {
        return alt().length() == 1 && ref().length() > 1 && ref().charAt(0) == alt().charAt(0);
    }
}
