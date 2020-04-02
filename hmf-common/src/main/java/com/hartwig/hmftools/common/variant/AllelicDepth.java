package com.hartwig.hmftools.common.variant;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Genotype;

public interface AllelicDepth {

    int totalReadCount();

    int alleleReadCount();

    default double alleleFrequency() {
        return (double) alleleReadCount() / totalReadCount();
    }

    static boolean containsAllelicDepth(final Genotype genotype) {
        return genotype.hasAD() && genotype.getAD().length > 1;
    }

    @NotNull
    static AllelicDepth fromGenotype(@NotNull final Genotype genotype) {
        Preconditions.checkArgument(genotype.hasAD());

        int[] adFields = genotype.getAD();
        final int alleleReadCount = adFields[1];

        int totalReadCount = 0;
        if (genotype.hasDP()) {
            totalReadCount = genotype.getDP();
        } else {
            for (final int afField : adFields) {
                totalReadCount += afField;
            }
        }

        return ImmutableAllelicDepthImpl.builder().alleleReadCount(alleleReadCount).totalReadCount(totalReadCount).build();
    }
}
