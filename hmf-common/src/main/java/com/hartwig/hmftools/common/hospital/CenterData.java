package com.hartwig.hmftools.common.hospital;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class CenterData {

    @NotNull
    public abstract String cpctRecipients();

    @NotNull
    public abstract String drupRecipients();

    @NotNull
    public abstract String addressName();

    @NotNull
    public abstract String addressZip();

    @NotNull
    public abstract String addressCity();

    @NotNull
    public abstract String cpctPI();

    @NotNull
    public abstract String drupPI();
}
