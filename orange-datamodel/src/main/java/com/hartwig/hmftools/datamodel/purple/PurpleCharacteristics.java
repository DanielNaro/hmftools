package com.hartwig.hmftools.datamodel.purple;

import com.hartwig.hmftools.datamodel.variant.msi.MicrosatelliteStatus;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class PurpleCharacteristics {

    public abstract boolean wholeGenomeDuplication();

    public abstract double microsatelliteIndelsPerMb();

    @NotNull
    public abstract MicrosatelliteStatus microsatelliteStatus();

    public abstract double tumorMutationalBurdenPerMb();

    @NotNull
    public abstract TumorMutationalStatus tumorMutationalBurdenStatus();

    public abstract int tumorMutationalLoad();

    @NotNull
    public abstract TumorMutationalStatus tumorMutationalLoadStatus();

    public abstract int svTumorMutationalBurden();
}
