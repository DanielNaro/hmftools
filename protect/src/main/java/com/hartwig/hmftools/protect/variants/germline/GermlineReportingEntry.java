package com.hartwig.hmftools.protect.variants.germline;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class GermlineReportingEntry {

    @NotNull
    public abstract String gene();

    public abstract boolean notifyClinicalGeneticist();

    public abstract boolean reportBiallelicOnly();

    @Nullable
    public abstract String exclusiveHgvsProteinFilter();
}
