package com.hartwig.hmftools.protect.actionability_v2.variant;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class ActionableVariant {

    @NotNull
    public abstract String gene();

    @NotNull
    public abstract String chromosome();

    @NotNull
    public abstract String position();

    @NotNull
    public abstract String ref();

    @NotNull
    public abstract String alt();

    @NotNull
    public abstract String source();

    @NotNull
    public abstract String drug();

    @NotNull
    public abstract String drugType();

    @NotNull
    public abstract String cancerType();

    @NotNull
    public abstract String level();

    @NotNull
    public abstract String direction();

    @NotNull
    public abstract String link();
}


