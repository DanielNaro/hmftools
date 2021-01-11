package com.hartwig.hmftools.common.variant;

import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.VariantContext;

public enum VariantType {
    MNP,
    SNP,
    INDEL,
    UNDEFINED;

    @NotNull
    public static VariantType type(@NotNull VariantContext context) {
        switch (context.getType()) {
            case MNP:
                return VariantType.MNP;
            case SNP:
                return VariantType.SNP;
            case INDEL:
                return VariantType.INDEL;
        }
        return VariantType.UNDEFINED;
    }
}
