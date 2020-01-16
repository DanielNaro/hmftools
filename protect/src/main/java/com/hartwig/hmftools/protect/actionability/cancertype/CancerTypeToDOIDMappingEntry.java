package com.hartwig.hmftools.protect.actionability.cancertype;

import java.util.Set;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
abstract class CancerTypeToDOIDMappingEntry {

    @NotNull
    abstract String cancerType();

    @NotNull
    abstract Set<String> doids();
}
