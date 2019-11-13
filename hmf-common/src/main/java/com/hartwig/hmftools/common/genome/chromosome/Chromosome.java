package com.hartwig.hmftools.common.genome.chromosome;

import com.hartwig.hmftools.common.purple.gender.Gender;

import org.jetbrains.annotations.NotNull;

public interface Chromosome {

    boolean isAutosome();

    boolean isAllosome();

    boolean isDiploid(@NotNull Gender gender);
}
