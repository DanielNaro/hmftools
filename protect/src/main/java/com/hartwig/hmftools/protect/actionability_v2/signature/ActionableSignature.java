package com.hartwig.hmftools.protect.actionability_v2.signature;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class ActionableSignature {

    @NotNull
    public abstract String signature();

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
