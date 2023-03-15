package com.hartwig.hmftools.datamodel.purple;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Gson.TypeAdapters
@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class PurpleFit {

    @NotNull
    public abstract PurpleQC qc();

    public abstract boolean hasSufficientQuality();

    @NotNull
    public abstract PurpleFittedPurityMethod fittedPurityMethod();

    public abstract boolean containsTumorCells();

    public abstract double purity();

    public abstract double minPurity();

    public abstract double maxPurity();

    public abstract double ploidy();

    public abstract double minPloidy();

    public abstract double maxPloidy();
}
