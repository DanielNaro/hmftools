package com.hartwig.hmftools.common.purple.purity;

import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.variant.msi.MicrosatelliteStatus;
import com.hartwig.hmftools.common.variant.tml.TumorMutationalStatus;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class PurityContext {

    public abstract String version();

    public abstract Gender gender();

    public abstract FittedPurity bestFit();

    public abstract FittedPurityStatus status();

    public abstract FittedPurityScore score();

    public abstract double polyClonalProportion();

    public abstract boolean wholeGenomeDuplication();

    public abstract double microsatelliteIndelsPerMb();

    public abstract double tumorMutationalBurdenPerMb();

    public abstract double tumorMutationalLoad();

    @NotNull
    public abstract MicrosatelliteStatus microsatelliteStatus();

    @NotNull
    public abstract TumorMutationalStatus tumorMutationalLoadStatus();

    @NotNull
    public abstract TumorMutationalStatus tumorMutationalBurdenStatus();
}
